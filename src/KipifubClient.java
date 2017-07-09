import javafx.geometry.Pos;
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
  int playerNumber;
  AreaNode[][] graph;
  int nodeSpacing;
  int[][] board;

  KipifubClient(int playerNumber){
    this.playerNumber = playerNumber;
  }

  public static void main(String[] args) {
    NetworkClient networkClient = new NetworkClient(null, "a");

    KipifubClient player = new KipifubClient(networkClient.getMyPlayerNumber()); // 0 = rot, 1 = grün, 2 = blau


    player.nodeSpacing = 50;
    player.graph = player.calcNavGraph(networkClient, player.nodeSpacing);


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

  MoveDirection handleColorChange(ColorChange colChange, Position currentGoal){
    if(colChange.player != playerNumber){
      //update representation or something similar...

      System.out.println("other colorChange...");
      System.out.println(colChange.toString());
      //System.out.println(
      //    "colorChange: player: "+ colChange.player
      //    +", bot:"+colChange.bot
      //    +", x: "+colChange.x
      //    +", y: "+colChange.y);
      return null;

    } else {
      //player is me
      System.out.println("my colorChange...");
      System.out.println(colChange.toString());

      Position currentPosition = new Position(colChange.x, colChange.y);
      //todo check if goal for certain bot was reached
      if(goalWasReached(colChange.bot, currentPosition, currentGoal)){
        return nextMoveDirection(colChange.bot, currentPosition);
      }
      //Set/update own bots move direction
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
      return walkToGoal(bot, currentPosition, newGoal);
    } else {
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

  AreaNode[][] calcNavGraph(NetworkClient networkClient, int nodeSpacing){
    //create graph for board
    int numberOfNodes = 1024/nodeSpacing;
    AreaNode[][] graph = new AreaNode[numberOfNodes][numberOfNodes];

    for(int x = 0; x < numberOfNodes; x++){
      for(int y = 0; y < numberOfNodes; y++){
        if(networkClient.isWalkable(x*nodeSpacing,y*nodeSpacing)){
          System.out.println("scaled x: "+x*nodeSpacing +", scaled y: "+ y*nodeSpacing+" is walkable");
          //make node, write node in list of nodes...
          AreaNode n = new AreaNode(x, y);
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

  //todo create modified quad tree

  // Quad Tree Node
  static class QTNode {
    //nodes from graph that belong to the quad tree element's area
    List<AreaNode> areaNodes;

    List<QTNode> children;

    //contains the QTNodes which contain information of this nodes subdivisions
    //if empty, then this node contains an area with smalles configured resolution


    int width;//this area's width
    int height;//this area's height

    Position position;//this nodes origin; area's center


  }

  /**
   * AreaNode Class
   *
   */
  class AreaNode {
    final Position position;
    final Position upperleft;
    final Position bottomright;

    List<AreaNode> neighbors = new ArrayList<>();

    AreaNode(int x, int y){
      this.position = new Position(x, y);
      this.upperleft = new Position(
          position.x - (nodeSpacing/2),
          position.y - (nodeSpacing/2));
      this.bottomright = new Position(
          position.x + (nodeSpacing/2) + (nodeSpacing%2),
          position.y + (nodeSpacing/2) + (nodeSpacing%2));
    }


    public int getMeanColor(){
      if(nodeSpacing > 1){
        return calcMeanColor(upperleft, bottomright);
      }
      else if(nodeSpacing == 1){
        return getBoard(position);
      } else {
        throw new IllegalStateException("Illegal nodeSpacing: "+ nodeSpacing);
      }
    }

    private int calcMeanColor(Position start, Position end){
      int r = 0;
      int g = 0;
      int b = 0;
      int sum = 0;

      for(int i = start.x; i <= end.x; i++){
        for(int j = start.y; j <= end.y; j++){
          int rgb = getBoard(new Position(i, j));
          r = r + (rgb >> 16) & 255;
          g = g + (rgb >> 8) & 255;
          b = b + rgb  & 255;
          sum++;
        }
      }

      r = r/sum;
      g = g/sum;
      b = b/sum;

      Color mean = new Color(r, g, b);

      return mean.getRGB();
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
