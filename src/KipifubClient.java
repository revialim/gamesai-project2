import javafx.geometry.Pos;
import lenz.htw.kipifub.ColorChange;
import lenz.htw.kipifub.net.NetworkClient;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
  Node[][] graph;
  int nodeSpacing;

  KipifubClient(int playerNumber){
    this.playerNumber = playerNumber;
  }

  public static void main(String[] args) {
    NetworkClient networkClient = new NetworkClient(null, "a");

    KipifubClient player = new KipifubClient(networkClient.getMyPlayerNumber()); // 0 = rot, 1 = grün, 2 = blau


    player.nodeSpacing = 50;
    player.graph = calcNavGraph(networkClient, player.nodeSpacing);


    ColorChange colorChange;
    while(networkClient.isAlive()) {

      while ((colorChange = networkClient.pullNextColorChange()) != null) {

        //verarbeiten von colorChange
        MoveDirection nextMove = player.handleColorChange(colorChange);
        //try {
        //  TimeUnit.SECONDS.sleep(1);
        //} catch (InterruptedException e) {
        //  e.printStackTrace();
        //}

        if(nextMove != null){
          networkClient.setMoveDirection(nextMove.bot, nextMove.x, nextMove.y);
        }
      }
    }
  }

  MoveDirection handleColorChange(ColorChange colChange){
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

      //Set/update own bots move direction
      Position currentPosition = new Position(colChange.x, colChange.y);
      return nextMoveDirection(colChange.bot, currentPosition);
    }
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
      int randomIndex = (int) Math.random() * (possibleGoals.size()-1);
      System.out.println("possibleGoals: "+possibleGoals.size()+", random: "+ randomIndex);
      newGoal = possibleGoals.get(randomIndex);
    }

    if(newGoal != null){
      return walkToGoal(bot, currentPosition, newGoal);
    } else {
      //if nothing around pos is walkable...
      return null;
    }
  }

  boolean isWalkableNode(Position nodePos){
    if(graph[nodePos.x][nodePos.y] != null){
      return true;
    } else {
      return false;
    }
  }

  //method for determining move direction according to goal and current position
  static MoveDirection walkToGoal(int bot, Position currPosition, Position goal){
    // -currPosition + goal --> move direction vector
    return new MoveDirection(bot, goal.x - currPosition.x, goal.y - currPosition.y);
  }

  static Node[][] calcNavGraph(NetworkClient networkClient, int nodeSpacing){
    //create graph for board
    int numberOfNodes = 1024/nodeSpacing;
    Node[][] graph = new Node[numberOfNodes][numberOfNodes];

    for(int x = 0; x < numberOfNodes; x++){
      for(int y = 0; y < numberOfNodes; y++){
        if(networkClient.isWalkable(x*nodeSpacing,y*nodeSpacing)){
          System.out.println("scaled x: "+x*nodeSpacing +", scaled y: "+ y*nodeSpacing+" is walkable");
          //make node, write node in list of nodes...
          Node n = new Node(x, y);
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

  //todo create modified quad tree

  // Quad Tree Node
  static class QTNode {
    //nodes from graph that belong to the quad tree element's area
    List<Node> areaNodes;

    //contains the QTNodes which contain information of this nodes subdivisions
    //if empty, then this node contains an area with smalles configured resolution
    List<QTNode> children;

    int size;//this areas size .. todo decide whether to use resolution or absolute number of pixels
    Position position;//this nodes origin... todo decide whether to use area's center or top left corner
  }

  static class Node {
    int x;
    int y;
    List<Node> neighbors = new ArrayList<>();

    Node(int x, int y){
      this.x = x;
      this.y = y;
    }
  }

  static class Position {
    int x;
    int y;

    Position(int x, int y){
      this.x = x;
      this.y = y;
    }
  }

  static class MoveDirection {
    int bot;
    int x;
    int y;
    //x y compose the vector for the move direction
    MoveDirection(int bot, int x, int y){
      this.bot = bot;
      this.x = x;
      this.y = y;
    }
  }

}
