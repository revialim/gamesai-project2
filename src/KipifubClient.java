import lenz.htw.kipifub.ColorChange;
import lenz.htw.kipifub.net.NetworkClient;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *
 //int rgb = networkClient.getBoard(x, y); // 0-1023
 //int b = rgb & 255;
 //int g = (rgb >> 8) & 255;
 //int r = (rgb >> 16) & 255;
 //
 //networkClient.getInfluenceRadiusForBot(0); // -> 40
 //
 //networkClient.getScore(0); // Punkte von rot
 //
 //networkClient.isWalkable(x, y); // begehbar oder Hinderniss?
 //
 //networkClient.setMoveDirection(0, 1, 0); // bot 0 nach rechts
 //networkClient.setMoveDirection(1, 0.23f, -0.52f); // bot 1 nach rechts unten
 //
 */

public class KipifubClient {
  private final static int size = 1024;
  private final static int qtDepth = 3;
  private final static int theSpacing = 20; //shouldn't be set bigger than 50 for accuracy

  private int playerNumber;
  private NavNode[][] navigationNodes;
  private int nodeSpacing;
  private NetworkClient networkClient;
  private QTNode qtRoot;

  private KipifubClient(int playerNumber, int nodeSpacing, NetworkClient networkClient){
    this.playerNumber = playerNumber;
    this.nodeSpacing = nodeSpacing;
    this.navigationNodes = calcNavGraph(networkClient, nodeSpacing);
    this.networkClient = networkClient;
    //initialize quad tree
    this.qtRoot = new QTNode(qtDepth, new Position(size/2, size/2), size);
  }

  public static void main(String[] args) {
    NetworkClient networkClient = new NetworkClient(null, "a");

    KipifubClient player = new KipifubClient(
        networkClient.getMyPlayerNumber(),
        theSpacing,
        networkClient);

    //new Painter(player); //insert if painter is done

    //could initialize directly with most interesting
    //picked a position on board for initial directions...
    Position currentGoal = player.qtRoot.getMostInteresting().position;//new Position(200,200);
    ColorChange colorChange;

    List<NavNode> mostInterestingNodes;
    int nodeIndex = 0;
    List<NavNode> pathToGoal = new ArrayList<>();

    while(networkClient.isAlive()) {

      while ((colorChange = networkClient.pullNextColorChange()) != null) {
        Position currentPos = new Position(colorChange.x, colorChange.y);

        System.out.println("Player / bot: "+ colorChange.player + " - "+ colorChange.bot
        +", goalwasReached: "+ goalWasReached(currentPos, currentGoal)
        +", currentgoal: "+ currentGoal.x + ", "+ currentGoal.y
        +", currentpos: "+currentPos.x +", "+ currentPos.y);

        if((pathToGoal.size() == 0) | goalWasReached(currentPos, currentGoal)){     	
          //calculate new goal, and new path to goal
          mostInterestingNodes = player.getInterestingNavNodes(player.qtRoot);

          System.out.println("recalculating new goal after goal was reached, mostInterestingNodes.size: "+mostInterestingNodes.size());
          nodeIndex = player.getInterestingNodeIndex(colorChange.bot);
          System.out.println("mostInterestingNodes.get("+nodeIndex+").position"
              + mostInterestingNodes.get(nodeIndex).position.x + ", "
              + mostInterestingNodes.get(nodeIndex).position.y + ", "+
              "\n current pos: "+currentPos.x + ", "+ currentPos.y);

          pathToGoal = player.createPathtoTarget(player.getTarget(currentPos, mostInterestingNodes.get(nodeIndex).position));
          System.out.print("path to goal created...\n");
        }

    	  MoveDirection nextMove = player.getNextMoveDirection(colorChange.bot, pathToGoal, currentPos);

        //networkClient.setMoveDirection(nextMove.bot, nextMove.direction.x, nextMove.direction.y);
    	  if (Math.abs(nextMove.direction.x) > Math.abs(nextMove.direction.y)){
    		  networkClient.setMoveDirection(nextMove.bot, nextMove.direction.x, 0);
    	  } else {
    		  networkClient.setMoveDirection(nextMove.bot,0, nextMove.direction.y);
    	  }
      }
    }
  }

  // ======= Paint Representation =======

  private BufferedImage getRepresentation(){
    int width = 1024;
    int height = 1024;
    BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

    for(int x = 0; x < width; x++){
      for (int y = 0; y < height; y++){
        bi.setRGB(x, y, getBoard(new Position(x, y)));
      }
    }

    return bi;
  }

  //TODO not done yet...
  static class Painter {
    private JFrame frame = new JFrame("Player's Board");

    Painter(KipifubClient kipifubClient){
      frame.setSize(1024,1024);
      frame.setVisible(true);
      frame.add(new ImagePanel(kipifubClient));
      frame.setDefaultCloseOperation(3);//WindowConstants.EXIT_ON_CLOSE
    }

    private class ImagePanel extends JPanel {

      private KipifubClient kipifubClient;

      private ImagePanel(KipifubClient kipifubClient) {
        this.kipifubClient = kipifubClient;
      }

      @Override
      protected void paintComponent(Graphics g) {
        BufferedImage image = kipifubClient.getRepresentation();
        //super.paintComponent(g);
        g.drawImage(image, 0, 0, 1024, 1024, this);
        System.out.println("drawing something");
      }
    }
  }

  //========= Methods ===========
  /**
   * @param qtNode , most probably a QuadTree root
   * @return a List of NavNodes containing the mostInteresting nodes
   * of the given qtNode's children
   */
  private List<NavNode> getInterestingNavNodes(QTNode qtNode){
    List<NavNode> interestingNodes = new ArrayList<>();
    for(int i = 0; i < qtNode.children.size(); i++){
      interestingNodes.add(qtNode.children.get(i).getMostInteresting());
    }

    //sort interesting nodes from most interesting to less, depending on the player's color
    interestingNodes.sort(Comparator.comparing(node -> (getDistance(node.getMeanColor(), getPlayerColor()))));

    return interestingNodes;
  }

  private int getInterestingNodeIndex(int bot){
    if(bot == 0){
      return 1;
    } if(bot == 1){
      return 0;
    } if(bot == 2){
      return 2;
    } else {
      throw new IllegalArgumentException("bot must be 0, 1, or 2");
    }
  }
  //void handleColorChange(ColorChange colorChange){
  //  //could do: update representation or something similar...
  //}

  private MoveDirection getNextMoveDirection(int bot, List<NavNode> path, Position currentPos){
    //path at 0 is goal, path at path.length-1 is start/ closest of path
    //find node on path closest to currentPosition
    //walk towards the node that comes after it
    int minDist = 1024 * 1024; //some large distance that is always bigger than a maximal distance of two points on the field
    int nextToMinIndex = 0;
    //NavNode closestCheckpoint = path.get(path.size()-1);
    
    if (goalWasReached(currentPos, path.get(path.size()-1).position) && path.size() != 1){
    	path.remove(path.size()-1);
    	System.out.println("Reduced path size, new size: " + path.size() + ", next Pos: " + path.get(path.size()-1).position.x + " / " + path.get(path.size()-1).position.y);
    }

    for(int i = 0; i < path.size(); i++){
      NavNode checkpoint = path.get(i);
      int tmpDist = calcEukDistance(checkpoint.position, currentPos);
      if(tmpDist < minDist) {
        minDist = tmpDist;
        //closestCheckpoint = checkpoint;
        if (i < path.size() - 2 && bot == 0) { //bot 0 should be the fastest (spray paint bottle)
          nextToMinIndex = i + 2;
        }
        else if (i < path.size() - 1) {
          nextToMinIndex = i + 1;
        } else {
          nextToMinIndex = i;
        }
      }
    }
    return walkToGoal(bot, currentPos, path.get(nextToMinIndex).position);

  }

  //calculate a target navigation node, so later a shortest path can be created to the target
  private NavNode getTarget(Position currentPosition, Position goalPosition){
    Position scaledPos = new Position(currentPosition.x/nodeSpacing, currentPosition.y/nodeSpacing);
    Position scaledGoal = new Position(goalPosition.x/nodeSpacing, goalPosition.y/nodeSpacing);

    setNavNodeWeights(scaledGoal);

    return aStern(navigationNodes[scaledPos.x][scaledPos.y], navigationNodes[scaledGoal.x][scaledGoal.y]);
  }

  private void setNavNodeWeights(Position scaledGoal){
    for (NavNode[] navNodesOnXAxis : navigationNodes) {
      for (NavNode navNode : navNodesOnXAxis) {
        if (navNode != null)
          navNode.setWeight(calcEukDistance(navNode.position, scaledGoal));
      }
    }
  }

  //method for determining if a given goal was reached
  private static boolean goalWasReached(Position currentPos, Position goal){
    return (currentPos.x <= goal.x + theSpacing*2)
        && (currentPos.y <= goal.y + theSpacing*2)
        && (currentPos.x >= goal.x - theSpacing*2)
        && (currentPos.y >= goal.y - theSpacing*2);
  }

  //private boolean isWalkableNode(Position nodePos){
  //  return (navigationNodes[nodePos.x][nodePos.y] != null);
  //}

  //method for determining move direction according to goal and current position
  private static MoveDirection walkToGoal(int bot, Position currPosition, Position goal){
    // -currPosition + goal --> move direction vector
    Position moveDirection = new Position(goal.x - currPosition.x, goal.y - currPosition.y);
    return new MoveDirection(bot, goal, moveDirection);
  }

  //create a navigation graph based on the given fields white/walkable and black/non-walkable areas and a given nodeSpacing
  private NavNode[][] calcNavGraph(NetworkClient networkClient, int nodeSpacing){
    //create navigationNodes for board
    int numberOfNodes = size/nodeSpacing;
    NavNode[][] graph = new NavNode[numberOfNodes][numberOfNodes];

    for(int x = 0; x < numberOfNodes; x++){
      for(int y = 0; y < numberOfNodes; y++){
        if(networkClient.isWalkable(x*nodeSpacing+nodeSpacing/2,y*nodeSpacing+nodeSpacing/2)){
          NavNode n = new NavNode(x*nodeSpacing+nodeSpacing/2, y*nodeSpacing+nodeSpacing/2);
          graph[x][y] = n;
        } 
      }
    }
    //add "edges", add neighboring nodes
    for(int x = 0; x < numberOfNodes; x++){
      for(int y = 0; y < numberOfNodes; y++) {
        if (graph[x][y] != null) {
          //node above
          if (y > 0 && graph[x][y - 1] != null) {
            graph[x][y].neighbors.add(graph[x][y - 1]);
          }
          //node left
          if (x > 0 && graph[x - 1][y] != null) {
            graph[x][y].neighbors.add(graph[x - 1][y]);
          }
          //node right
          if ((x < numberOfNodes-1) && graph[x + 1][y] != null) {
            graph[x][y].neighbors.add(graph[x + 1][y]);
          }
          //node below
          if ((y < numberOfNodes-1) && graph[x][y + 1] != null) {
            graph[x][y].neighbors.add(graph[x][y + 1]);
          }
          //...possible to also add nodes diagonal to current node...
        }
      }
    }

    return graph;
  }

  // ======= ASTAR =======

  //TODO add some way of weighting the costs to the nodes based on the color's interestingness

  private NavNode aStern(NavNode startPos, NavNode goalPos){
    //todo refactor
  	List<NavNode> openList = new ArrayList<>();
	  List<NavNode> closedList = new ArrayList<>();
	  //add start Node
	  openList.add(startPos);

	  while (openList.size() > 0){
	  	// search and remove node from openList with smallest coasts
	  	NavNode current = openList.get(0);
	  	int currentIdx = 0;
	  	for (int i=0; i < openList.size(); i++){
	  		if (openList.get(i).getWeight() < current.getWeight()){
	  			current = openList.get(i);
	  			currentIdx = i;
	  		}
	  	}
	  	openList.remove(currentIdx);

	  	// goal reached?
	  	if (current == goalPos) {
	  		return current;
	  	}

	  	// for all neighbors
	  	for (int i=0; i < current.neighbors.size(); i++){
	  		boolean inClosedList = false;
	  		for (NavNode node : closedList) {
	  			if (node == current.neighbors.get(i)) inClosedList = true;
	  		}
	  		if (!inClosedList){
	  			// is neighbor already in openList?
	  			boolean inOpenList = false;
	  			for (NavNode node : openList) {
	  				if (node == current.neighbors.get(i)) inOpenList = true;
	  			}
	  			if (!inOpenList){
	  				current.neighbors.get(i).setWeight(Integer.MAX_VALUE);
	  				openList.add(current.neighbors.get(i));
	  			}
	  			if (current.getWeight() < current.neighbors.get(i).getWeight()){
	  				current.neighbors.get(i).setWeight(calcEukDistance(current.neighbors.get(i).position, goalPos.position)); // TODO
	  				current.neighbors.get(i).setParent(current);
	  				// goal reached?
	  				if (current.neighbors.get(i) == goalPos) {
	  					return current.neighbors.get(i);
	  				}
	  			}
	  		}
	  	}
	  	closedList.add(current);
	  }

	  throw new IllegalStateException("No path was found!");
  }
  
  private int calcEukDistance(Position startPos, Position goalPos){
	  return (int) Math.sqrt((Math.pow((startPos.x - goalPos.x), 2) + Math.pow((startPos.y - goalPos.y), 2)));
  }
  
  private List<NavNode> createPathtoTarget(NavNode targetNode){
    if(targetNode != null){
      List<NavNode> path = new ArrayList<>();
      NavNode current = targetNode;
      while (current.getParent() != null) {
        path.add(current);
        current = current.getParent();
        //System.out.println(current.position.x + " / " + current.position.y);
      }
      return path;
    } else {
      throw new IllegalStateException("No target available to create a path to!");
    }
  }

  // ======= END A STAR =====

  private int getBoard(Position pos){
    return networkClient.getBoard(pos.x, pos.y);
  }

  private static int calcMeanColor(int[] colors){
    int sum = 0;
    int r = 0;
    int g = 0;
    int b = 0;

    for(int color : colors){
      r = r + ((color >> 16) & 255);
      g = g + ((color >> 8)  & 255);
      b = b + ( color        & 255);
      sum++;

     // System.out.println("r: "+ r + ", g: "+g+", b: "+b);
    }

    if (sum != 0){
	    r = r/sum;
	    g = g/sum;
	    b = b/sum;
    }

    Color mean = new Color(r, g, b);

    //System.out.println("calcMeanColor: "+r+", "+g+", "+b);
    return mean.getRGB();
  }

  //Manhattan Distance
  private static int getDistance(int col1, int col2){
    int r1 = (col1 >> 16) & 255;
    int g1 = (col1 >> 8) & 255;
    int b1 = col1  & 255;
    int r2 = (col2 >> 16) & 255;
    int g2 = (col2 >> 8) & 255;
    int b2 = col2 & 255;

    return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
  }

  private int getPlayerColor(){
    if(playerNumber == 0){
      return Color.RED.getRGB();
    }
    if(playerNumber == 1){
      return Color.GREEN.getRGB();
    }
    if(playerNumber == 2){
      return Color.BLUE.getRGB();
    }
    else {
      throw new IllegalStateException("player number: "+playerNumber+" has been initialised wrongly. Should be 0,1 or 2.");
    }
  }

 //============ Classes =============

  // Quad Tree Node
  class QTNode {
    int depth;//depth of tree node; while 0 depth means this node is a child
    int size;//this area's width or height, same since it's a square
    Position origin;//this node's origin or this area's center

    Position upperLeft;
    Position lowerRight;

    //nodes from navigationNodes that belong to the quad tree element's area
    List<NavNode> navNodes = new ArrayList<>();

    //contains the QTNodes which contain information of this nodes subdivisions
    //if empty, then this node contains an area with smallest configured resolution
    List<QTNode> children = new ArrayList<>();

    // ==== constructor ====
    QTNode(int depth, Position origin, int size){
      //System.out.println("new QTNode, depth: "+depth+", origin x: "+origin.x+", y: "+origin.y+", size: "+size);
      this.depth = depth;
      this.origin = origin;
      this.size = size;

      upperLeft = new Position(origin.x - size/2, origin.y - size/2); //include in area
      lowerRight = new Position(origin.x + size/2 + size%2, origin.y + size/2 + size%2); //exclude from area

      if(this.depth > 0){
        //create own children recursively
        children.add(new QTNode(depth-1, new Position(origin.x - size/4, origin.y - size/4), size/2));
        children.add(new QTNode(depth-1, new Position(origin.x - size/4, origin.y + size/4), size/2));
        children.add(new QTNode(depth-1, new Position(origin.x + size/4, origin.y - size/4), size/2));
        children.add(new QTNode(depth-1, new Position(origin.x + size/4, origin.y + size/4), size/2));
      }

      //Initialize navNodes that lie in this QTNodes area
      for(int i = 0; i < navigationNodes.length; i++){
        for(int j = 0; j< navigationNodes[i].length; j++){
          if(navigationNodes[i][j] != null && contains(navigationNodes[i][j].position)){
            navNodes.add(navigationNodes[i][j]);
          }
        }
      }
    }

    //the mean color of this QTNode
    int getMeanColor(){
      int[] colors;
      int i = 0;
      if(children.size() == 0){//end of recursion
        colors = new int[navNodes.size()];
        for(NavNode navNode: navNodes){
          colors[i] = navNode.getMeanColor();
          i++;
        }
      }
      else {
        colors = new int[children.size()];
        for (QTNode child : children) {
          //the mean color of the child should be the updated mean color now
          colors[i] = child.getMeanColor();
          i++;
        }
      }

      return calcMeanColor(colors);
    }

    //determines whether a position lies inside this QTNode
    boolean contains(Position pos){
      return ((pos.x >= upperLeft.x && pos.y >= upperLeft.y)&&(pos.x < lowerRight.x && pos.y < lowerRight.y));
    }

    //returns the most interesting navigation node that lies inside this QTNode
    NavNode getMostInteresting(){
      if(children.size() == 0){//end of recursion
        System.out.println("most interesting nav node: "
            +     navNodes.get(navNodes.size()/2).position.x
            +", "+navNodes.get(navNodes.size()/2).position.y
            +", red: "+ ((navNodes.get(navNodes.size()/2).getMeanColor() >> 16) & 255)
        );
        return navNodes.get(navNodes.size()/2);//median of navNodes
      }
      else {
      //find child with most interesting mean color
      QTNode interesting = children.get(0);

      for(int i = 1; i < children.size(); i++){
        QTNode child = children.get(i);
        if(getDistance(child.getMeanColor(), getPlayerColor()) > getDistance(interesting.getMeanColor(), getPlayerColor())){
          interesting = child;
        }
      }
      return interesting.getMostInteresting();
      }
    }

  }

  /**
   * NavNode Class
   * Stores information of an area
   * mainly used for navigation
   * including:
   *   position,
   *   mean color and
   *   direct neighbors
   */
  class NavNode {
    final Position position;
    final Position upperLeft;
    final Position bottomRight;
    int weight;
    NavNode parent;

    List<NavNode> neighbors = new ArrayList<>();

    NavNode(int x, int y){
      this.position = new Position(x, y);
      this.upperLeft = new Position(
          position.x - (nodeSpacing/2),
          position.y - (nodeSpacing/2));
      this.bottomRight = new Position(
          position.x + (nodeSpacing/2) + (nodeSpacing%2),
          position.y + (nodeSpacing/2) + (nodeSpacing%2));
      this.weight = 1;
    }

    private int getWeight(){
    	return this.weight;
    }
    
    private void setWeight(int weight){
    	this.weight = weight;
    }
    
    private NavNode getParent(){
    	return this.parent;
    }
    
    private void setParent(NavNode parent){
    	this.parent = parent;
    }

    /**
     * Call from outside to always get up-to-date mean color
     * (not cached)
     * If area size is one pixel, the color of that pixel is
     * returned. Otherwise the mean color of the area is
     * calculated in calcMeanColor.
     * @return mean color of this navNode's pixels
     */
    private int getMeanColor(){
      if(nodeSpacing > 1){
        return this.calcMeanColor(upperLeft, bottomRight);
      }
      else if(nodeSpacing == 1){
        return getBoard(position);
      } else {
        throw new IllegalStateException("Illegal nodeSpacing: "+ nodeSpacing);
      }
    }

    private int calcMeanColor(Position start, Position end){
      int[] colors = new int[(nodeSpacing+1)*(nodeSpacing+1)];
          //new int[(end.x-start.x+1)*(end.y-start.y+1)];
      int i = 0;

      //System.out.println("start calc array");
      for(int x = start.x; x <= end.x; x++){
        for(int y = start.y; y <= end.y; y++){
          colors[i] = getBoard(new Position(x, y));
          i++;
        }
      }

      //System.out.println(" nav node some color red part: "+ ((colors[0] >> 16) & 255) );

      return KipifubClient.calcMeanColor(colors);
    }

  }

  static class MoveDirection {
    final int bot;
    final Position goal;
    final Position direction;
    //x y compose the vector for the move direction
    MoveDirection(int bot, Position goal, Position direction){
      this.bot = bot;
      this.goal = goal;
      this.direction = direction;
    }
  }

  static class Position {
    final int x;
    final int y;

    Position(int x, int y){
      this.x = x;
      this.y = y;
    }
  }

}
