import lenz.htw.kipifub.ColorChange;
import lenz.htw.kipifub.net.NetworkClient;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
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
  static int playerNumber;
  NavNode[][] graph;
  int nodeSpacing;
  int[][] board;//pixels of board

  KipifubClient(int playerNumber, int nodeSpacing, NetworkClient networkClient){
    this.playerNumber = playerNumber;
    this.nodeSpacing = nodeSpacing;
    this.graph = calcNavGraph(networkClient, nodeSpacing);
  }

  public static void main(String[] args) {
    NetworkClient networkClient = new NetworkClient(null, "a");

    KipifubClient player = new KipifubClient(
        networkClient.getMyPlayerNumber(),
        50,
        networkClient); // 0 = rot, 1 = grün, 2 = blau

    Position currentGoal = new Position(0,0);
    ColorChange colorChange;
    while(networkClient.isAlive()) {

      while ((colorChange = networkClient.pullNextColorChange()) != null) {

        //verarbeiten von colorChange
        MoveDirection nextMove = player.handleColorChange(colorChange, currentGoal);
        //try {
        //  TimeUnit.SECONDS.sleep(1);
        //} catch (InterruptedException e) {
        //  e.printStackTrace();
        //}

        if(nextMove != null){
          //set or update move direction and currentGoal information
          networkClient.setMoveDirection(nextMove.bot, nextMove.direction.x, nextMove.direction.y);
          currentGoal = new Position(nextMove.goal.x, nextMove.goal.y);
        }
      }
    }
  }

  //========= Methods ===========

  MoveDirection handleColorChange(ColorChange colChange, Position currentGoal){
    //update representation or something similar...

    System.out.println(colChange.toString());

    if(colChange.player == playerNumber) {
      Position currentPosition = new Position(colChange.x, colChange.y);
      //check if goal for certain bot was reached
      if (goalWasReached(colChange.bot, currentPosition, currentGoal)) {
        return nextMoveDirection(colChange.bot, currentPosition);
      }
      if( currentGoal.x == 0 && currentGoal.y == 0){
        return nextMoveDirection(colChange.bot, currentPosition);
      }
    }
    return null;
  }


  //method for determining if goal was reached
  static boolean goalWasReached(int bot, Position currentPos, Position goal){
    return (currentPos.x <= goal.x+10)
        && (currentPos.y <= goal.y+10)
        && (currentPos.x >= goal.x-10)
        && (currentPos.y >= goal.y-10);
  }

  MoveDirection nextMoveDirection(int bot, Position currentPosition){
    Position scaledPos = new Position(currentPosition.x/nodeSpacing, currentPosition.y/nodeSpacing);
    // check up, left, bottom, right for walkability
    Position newGoal = null;
    List<Position> possibleGoals = new ArrayList<>();

    if(isWalkableNode(new Position(scaledPos.x-1, scaledPos.y))){
      //left
      possibleGoals.add(new Position(currentPosition.x - nodeSpacing, currentPosition.y));
    } else if(isWalkableNode(new Position(scaledPos.x+1, scaledPos.y))){
      //right
      possibleGoals.add(new Position(currentPosition.x + nodeSpacing, currentPosition.y));
    } else if(isWalkableNode(new Position(scaledPos.x, scaledPos.y-1))) {
      //up
      possibleGoals.add(new Position(currentPosition.x, currentPosition.y - nodeSpacing));
    } else if(isWalkableNode(new Position(scaledPos.x, scaledPos.y+1))) {
      //down
      possibleGoals.add(new Position(currentPosition.x, currentPosition.y + nodeSpacing));
    }

    if(possibleGoals.size() > 0){
      //int randomIndex = (int) (Math.random() * (possibleGoals.size()-1));
      //System.out.println("possibleGoals: "+possibleGoals.size()+", random: "+ randomIndex);
      //newGoal = possibleGoals.get(randomIndex);
      Collections.shuffle(possibleGoals);
      newGoal = possibleGoals.get(0);
    }

    if(newGoal != null){
      System.out.println("walk to goal with bot:"+ bot +"; current: "+ currentPosition.x + ", "+ currentPosition.y+"; new Goal: "+ newGoal.x
      +", "+ newGoal.y);
      return walkToGoal(bot, currentPosition, newGoal);
    } else {
      System.out.println("nothing walkable");
      //if nothing around pos is walkable...
      return null;
    }
  }

  boolean isWalkableNode(Position nodePos){
    return (graph[nodePos.x][nodePos.y] != null);
  }

  //method for determining move direction according to goal and current position
  static MoveDirection walkToGoal(int bot, Position currPosition, Position goal){
    // -currPosition + goal --> move direction vector
    Position moveDirection = new Position(goal.x - currPosition.x, goal.y - currPosition.y);
    return new MoveDirection(bot, goal, moveDirection);
  }

  NavNode[][] calcNavGraph(NetworkClient networkClient, int nodeSpacing){
    //create graph for board
    int numberOfNodes = 1024/nodeSpacing;
    NavNode[][] graph = new NavNode[numberOfNodes][numberOfNodes];

    for(int x = 0; x < numberOfNodes; x++){
      for(int y = 0; y < numberOfNodes; y++){
        if(networkClient.isWalkable(x*nodeSpacing,y*nodeSpacing)){
          System.out.println("scaled x: "+x*nodeSpacing +", scaled y: "+ y*nodeSpacing+" is walkable");
          //make node, write node in list of nodes...
          NavNode n = new NavNode(x+nodeSpacing/2, y+nodeSpacing/2);
          graph[x][y] = n;
        }
      }
    }
    //add "edges", add neighboring nodes
    for(int x = 0; x < numberOfNodes; x++){
      for(int y = 0; y < numberOfNodes; y++) {
        if (graph[x][y] != null) {
          //node above
          if (graph[x][y - 1] != null) {
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

  private int getBoard(Position pos){
    return board[pos.x][pos.y];
  }

  private static int calcMeanColor(int[] colors){
    int sum = 0;
    int r = 0;
    int g = 0;
    int b = 0;

    for(int color : colors){
      //the mean color of the child should be the updated mean color now
      r = r + (color >> 16) & 255;
      g = g + (color >> 8) & 255;
      b = b + color  & 255;
      sum++;
    }

    r = r/sum;
    g = g/sum;
    b = b/sum;

    Color mean = new Color(r, g, b);

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

  private static int getPlayerColor(){
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

  //todo create modified quad tree
 //============ Classes =============

  // Quad Tree Node
  class QTNode {
    int depth;//depth of tree node; while 0 depth means this node is a child
    int size;//this area's width or height, same since it's a square
    Position origin;//this nodes origin; area's center

    Position upperLeft = new Position(origin.x - size/2, origin.y - size/2); //include in area
    Position lowerRight = new Position(origin.x + size/2 + size%2, origin.y + size/2 + size%2); //exclude from area

    //mean color of the QTNode's area
    int meanColor;

    //nodes from graph that belong to the quad tree element's area
    List<NavNode> navNodes;

    //contains the QTNodes which contain information of this nodes subdivisions
    //if empty, then this node contains an area with smallest configured resolution
    List<QTNode> children;

    // ==== constructor ====
    QTNode(int depth, Position origin, int size){
      this.depth = depth;
      this.origin = origin;
      this.size = size;

      if(this.depth > 0){
        //create own children recursively
        children.add(new QTNode(depth-1, new Position(origin.x - size/4, origin.y - size/4), size/2));
        children.add(new QTNode(depth-1, new Position(origin.x - size/4, origin.y + size/4), size/2));
        children.add(new QTNode(depth-1, new Position(origin.x + size/4, origin.y - size/4), size/2));
        children.add(new QTNode(depth-1, new Position(origin.x + size/4, origin.y + size/4), size/2));
      }
      //todo set up meanColor

    }
    // ====================

    void updateMeanColor(Position hit){
      if((hit.x >= upperLeft.x && hit.y >= upperLeft.y)&&(hit.x < lowerRight.x && hit.y < lowerRight.y)){
        //update mean color of children
        int[] colors = new int[children.size()];
        int i = 0;

        for(QTNode child : children){
          child.updateMeanColor(hit);
          //the mean color of the child should be the updated mean color now
          colors[i] = child.meanColor;
          i++;
        }

        meanColor = calcMeanColor(colors);
      }
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
        if(getDistance(child.meanColor, getPlayerColor()) > getDistance(interesting.meanColor, getPlayerColor())){
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

    List<NavNode> neighbors = new ArrayList<>();

    NavNode(int x, int y){
      this.position = new Position(x, y);
      this.upperLeft = new Position(
          position.x - (nodeSpacing/2),
          position.y - (nodeSpacing/2));
      this.bottomRight = new Position(
          position.x + (nodeSpacing/2) + (nodeSpacing%2),
          position.y + (nodeSpacing/2) + (nodeSpacing%2));
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
        return calcMeanColor(upperLeft, bottomRight);
      }
      else if(nodeSpacing == 1){
        return getBoard(position);
      } else {
        throw new IllegalStateException("Illegal nodeSpacing: "+ nodeSpacing);
      }
    }

    private int calcMeanColor(Position start, Position end){
      int[] colors = new int[(end.x-start.x+1)*(end.y-start.y+1)];
      int i = 0;

      for(int x = start.x; x <= end.x; x++){
        for(int y = start.y; y <= end.y; y++){
          colors[i] = getBoard(new Position(x, y));
          i++;
        }
      }

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
