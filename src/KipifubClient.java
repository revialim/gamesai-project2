import lenz.htw.kipifub.ColorChange;
import lenz.htw.kipifub.net.NetworkClient;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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
    this.qtRoot = new QTNode(2, new Position(size/2, size/2), size);
  }

  public static void main(String[] args) {
    NetworkClient networkClient = new NetworkClient(null, "a");

    KipifubClient player = new KipifubClient(
        networkClient.getMyPlayerNumber(),
        20,
        networkClient); // 0 = rot, 1 = grün, 2 = blau

    //new Painter(player); //todo

    Position currentGoal = new Position(0,0); // TODO initializing directly with most interesting
    ColorChange colorChange;
    List<NavNode> path = new ArrayList<>();

    List<NavNode> mostInterestingNodes;
    List<NavNode> pathToGoal = new ArrayList<>();

    while(networkClient.isAlive()) {

      while ((colorChange = networkClient.pullNextColorChange()) != null) {
        //player.handleColorChange(colorChange); //todo
        Position currentPos = new Position(colorChange.x, colorChange.y);

        if((pathToGoal.size() == 0) || goalWasReached(currentPos, currentGoal)){
          //calculate new goal, and new path to goal
          mostInterestingNodes = player.getInterestingNavNodes(player.qtRoot);
          pathToGoal = player.createPathtoTarget(player.getTarget(currentPos, mostInterestingNodes.get(0).position));
        }
    	  MoveDirection nextMove = player.getNextMoveDirection(colorChange.bot, pathToGoal, currentPos);

	      //Collections.sort(mostInteresting);
	      //currentGoal = mostInteresting.get(0).position;

        //verarbeiten von colorChange
        //MoveDirection nextMove = player.handleColorChange(colorChange, currentGoal);

        //if(nextMove != null){
        //	System.out.println("Next Move is: " + nextMove.bot + " ; " + nextMove.direction.x + "/" + nextMove.direction.y + " ; " + nextMove.goal.x + "/" + nextMove.goal.y);
        //	//set or update move direction and currentGoal information
        //  networkClient.setMoveDirection(nextMove.bot, nextMove.direction.x, nextMove.direction.y);
        //  currentGoal = new Position(nextMove.goal.x, nextMove.goal.y);
        //}

        networkClient.setMoveDirection(nextMove.bot, nextMove.direction.x, nextMove.direction.y);
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
    return interestingNodes;
  }

  //void handleColorChange(ColorChange colorChange){
  //  //todo update representation or something similar...
  //}

  private MoveDirection getNextMoveDirection(int bot, List<NavNode> path, Position currentPos){
    //path at 0 is start, path at path.length-1 is end/goal of path
    //find node on path closest to currentPosition
    //walk towards the node that comes after it
    int minDist = 1024 * 1024;
    int nextToMinIndex = 0;
    NavNode closestCheckpoint = path.get(path.size()-1);

    for(int i = 0; i < path.size(); i++){
      NavNode checkpoint = path.get(i);
      int tmpDist = calcEukDistance(checkpoint.position, currentPos);
      if(tmpDist < minDist) {
        minDist = tmpDist;
        closestCheckpoint = checkpoint;
        if (i < path.size() - 1) {
          nextToMinIndex = i + 1;
        } else {
          nextToMinIndex = i;
        }
      }
    }
    return walkToGoal(bot, currentPos, path.get(nextToMinIndex).position);

  }
  private NavNode getTarget(Position currentPosition, Position goalPosition){
    Position scaledPos = new Position(currentPosition.x/nodeSpacing, currentPosition.y/nodeSpacing);
    Position scaledGoal = new Position(goalPosition.x/nodeSpacing, goalPosition.y/nodeSpacing);

    setNavNodeWeights(scaledGoal);

    return aStern(navigationNodes, navigationNodes[scaledPos.x][scaledPos.y], navigationNodes[scaledGoal.x][scaledGoal.y]);
  }

  private void setNavNodeWeights(Position scaledGoal){
    for(int i=0; i < navigationNodes.length; i++){
    	for(int j=0; j < navigationNodes[i].length; j++){
    		if (navigationNodes[i][j] != null)
    			navigationNodes[i][j].setWeight(calcEukDistance(navigationNodes[i][j].position, scaledGoal));
    	}
    }
  }
  //private MoveDirection handleColorChange(ColorChange colChange, Position currentGoal){
  //  //update representation or something similar...
//
  // // System.out.println(colChange.toString());
//
  //  if(colChange.player == playerNumber) {
  //    Position currentPosition = new Position(colChange.x, colChange.y);
  //    //check if goal for certain bot was reached
  //    //System.out.println("currentPos: " + colChange.x + "/" + colChange.y);
  //    //System.out.println("currentGoal: " + currentGoal.x + "/" + currentGoal.y);
  //    if (!goalWasReached(currentPosition, currentGoal)) {
  //      return nextMoveDirection(colChange.bot, currentPosition, currentGoal);
  //    }
  //    if( currentGoal.x == 0 && currentGoal.y == 0){
  //      return nextMoveDirection(colChange.bot, currentPosition, currentGoal);
  //    }
  //  }
  //  return null;
  //}

  //method for determining if goal was reached
  private static boolean goalWasReached(Position currentPos, Position goal){
    return (currentPos.x <= goal.x+20)
        && (currentPos.y <= goal.y+20)
        && (currentPos.x >= goal.x-20)
        && (currentPos.y >= goal.y-20);
  }

  //private MoveDirection nextMoveDirection(int bot, Position currentPosition, Position goalPosition){
  //  Position scaledPos = new Position(currentPosition.x/nodeSpacing, currentPosition.y/nodeSpacing);
  //  // check up, left, bottom, right for walkability
  //  Position newGoal = new Position(goalPosition.x/nodeSpacing, goalPosition.y/nodeSpacing);
  //
  //  // TODO add color multiplication to weight
  //  for(int i=0; i < navigationNodes.length; i++){
  //  	for(int j=0; j < navigationNodes[i].length; j++){
  //  		if (navigationNodes[i][j] != null)
  //  			navigationNodes[i][j].setWeight(calcEukDistance(navigationNodes[i][j].position, newGoal));
  //  	}
  //  }
  //  NavNode target = aStern(navigationNodes, navigationNodes[scaledPos.x][scaledPos.y], navigationNodes[newGoal.x][newGoal.y]);
  //  //System.out.println("A-Star target is: " + target.position.x + " / " + target.position.y);
  //
  //  List<NavNode> path =  createPathtoTarget(target);
  //
  //  //System.out.println("New Goal is: " + path.get(0).position.x + " / " + path.get(0).position.y);
  //  //return new MoveDirection(bot, newGoal, path.get(0).position);
  //  // TODO must it be first or last path point ?
  //  return walkToGoal(bot, currentPosition, path.get(path.size()-1).position);
  //  //return walkToGoal(bot, currentPosition, path.get(0).position);
    
    //List<Position> possibleGoals = new ArrayList<>();

    //if(isWalkableNode(new Position(scaledPos.x-1, scaledPos.y))){
    //  //left
    //  possibleGoals.add(new Position(currentPosition.x - nodeSpacing, currentPosition.y));
    //} else if(isWalkableNode(new Position(scaledPos.x+1, scaledPos.y))){
    //  //right
    //  possibleGoals.add(new Position(currentPosition.x + nodeSpacing, currentPosition.y));
    //} else if(isWalkableNode(new Position(scaledPos.x, scaledPos.y-1))) {
    //  //up
    //  possibleGoals.add(new Position(currentPosition.x, currentPosition.y - nodeSpacing));
    //} else if(isWalkableNode(new Position(scaledPos.x, scaledPos.y+1))) {
    //  //down
    //  possibleGoals.add(new Position(currentPosition.x, currentPosition.y + nodeSpacing));
    //}

    //if(possibleGoals.size() > 0){
    //  //int randomIndex = (int) (Math.random() * (possibleGoals.size()-1));
    //  //System.out.println("possibleGoals: "+possibleGoals.size()+", random: "+ randomIndex);
    //  //newGoal = possibleGoals.get(randomIndex);
    //  Collections.shuffle(possibleGoals);
    //  newGoal = possibleGoals.get(0);
    //}

    //if(newGoal != null){
    //  //System.out.println("walk to goal with bot:"+ bot
    //  //    +"; current: "+ currentPosition.x + ", "+ currentPosition.y
    //  //    +"; new Goal: "+ newGoal.x +", "+ newGoal.y);
    //  return walkToGoal(bot, currentPosition, newGoal);
    //} else {
    //  System.out.println("nothing walkable");
    //  //if nothing around pos is walkable...
    //  return null;
    //}

  //}

  private boolean isWalkableNode(Position nodePos){
    return (navigationNodes[nodePos.x][nodePos.y] != null);
  }

  //method for determining move direction according to goal and current position
  private static MoveDirection walkToGoal(int bot, Position currPosition, Position goal){
    // -currPosition + goal --> move direction vector
    Position moveDirection = new Position(goal.x - currPosition.x, goal.y - currPosition.y);
    return new MoveDirection(bot, goal, moveDirection);
  }

  private NavNode[][] calcNavGraph(NetworkClient networkClient, int nodeSpacing){
    //create navigationNodes for board
    int numberOfNodes = 1024/nodeSpacing;
    NavNode[][] graph = new NavNode[numberOfNodes][numberOfNodes];

    for(int x = 0; x < numberOfNodes; x++){
      for(int y = 0; y < numberOfNodes; y++){
        if(networkClient.isWalkable(x*nodeSpacing+nodeSpacing/2,y*nodeSpacing+nodeSpacing/2)){
          //System.out.println("scaled x: "+x*nodeSpacing +", scaled y: "+ y*nodeSpacing+" is walkable");
          //make node, write node in list of nodes...
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

  public NavNode aStern(NavNode[][] nodes, NavNode startPos, NavNode goalPos){
  	List<NavNode> openList = new ArrayList<NavNode>();
	  List<NavNode> closedList = new ArrayList<NavNode>();
	  //add start Node
	  openList.add(startPos);

	  while (!openList.isEmpty()){
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
	  	//if (!current.neighbors.get(i).getOccupied()){ // neighbors are always free
	  		// is neighbor already in closedList?
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
	  	//}
	  	}
	  	closedList.add(current);
	  }
	  System.out.println("No path was found!");
	  return null;
  }
  
  private int calcEukDistance(Position startPos, Position goalPos){
	  return (int) Math.sqrt((Math.pow((startPos.x - goalPos.x), 2) + Math.pow((startPos.y - goalPos.y), 2)));
  }
  
  public List<NavNode> createPathtoTarget(NavNode targetNode){
    if(targetNode != null){
      List<NavNode> path = new ArrayList<>();
      NavNode current = targetNode;
      while (current.getParent() != null) {
        path.add(current);
        current = current.getParent();
      }
		/*
		for(int i=0; i < path.size(); i++){
			System.out.println("Path node " + i +" pos: " + path.get(i).position.x + " / " + path.get(i).position.y);
		}
		*/
      return path;
    } else {
      System.out.println("No target available to create a path to!");
      return null;
    }
  }

  // ======= END A STAR =====

  private int getBoard(Position pos){
    int color = networkClient.getBoard(pos.x, pos.y);
    //System.out.println("color at pos (in getBoard): "+ pos.x + ", "+ pos.y+ "; color: "+ ((color >> 16)& 255));
    return color;
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

    r = r/sum;
    g = g/sum;
    b = b/sum;

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
    Position origin;//this nodes origin; area's center

    Position upperLeft;
    Position lowerRight;
    //mean color of the QTNode's area
    //private int meanColor;

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
        //System.out.println("with children size: "+children.size());
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
    // ====================

    int getMeanColor(){
      int[] colors;
      int i = 0;

      System.out.println("children size: "+children.size());
      System.out.println("navnodes size: " +navNodes.size());
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

    boolean contains(Position pos){
      return ((pos.x >= upperLeft.x && pos.y >= upperLeft.y)&&(pos.x < lowerRight.x && pos.y < lowerRight.y));
    }

    NavNode getMostInteresting(){
      if(children != null ){//recursion anchor
        return navNodes.get(navNodes.size()/2);//median of navNodes todo maybe adjust
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
  class NavNode implements Comparable<NavNode> {
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

    public int getWeight(){
    	return this.weight;
    }
    
    public void setWeight(int weight){
    	this.weight = weight;
    }
    
    public NavNode getParent(){
    	return this.parent;
    }
    
    public void setParent(NavNode parent){
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
    public int getMeanColor(){
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
      //System.out.println("end to start of nav node calcMeanColor: "+(end.x-start.x)+" nodespacing: "+nodeSpacing);

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

	public int compareTo(NavNode node) {
    	int compareColor = calcMeanColor(node.upperLeft, node.bottomRight);
    	//return calcMeanColor(this.upperLeft, this.bottomRight) - compareColor; //ascending order
    	return compareColor - calcMeanColor(this.upperLeft, this.bottomRight); //descending order
	}
	/*
	public Comparator<NavNode> NavNodeComparator = new Comparator<NavNode>() {
	
	public int compare(NavNode fruit1, NavNode fruit2) {
	
		int fruitName1 = fruit1.calcMeanColor(fruit2.upperLeft, fruit2.bottomRight);
		int fruitName2 = fruit2.calcMeanColor(fruit2.upperLeft, fruit2.bottomRight);
		
		//ascending order
		return fruitName1.compareTo(fruitName2);
		
		//descending order
		//return fruitName2.compareTo(fruitName1);
	}
	
	};
	 */
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
