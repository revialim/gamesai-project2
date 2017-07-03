import javafx.geometry.Pos;
import lenz.htw.kipifub.ColorChange;
import lenz.htw.kipifub.net.NetworkClient;

import java.awt.*;
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
  int playerNumber;
  Node[][] graph;
  int nodeSpacing;

  KipifubClient(int playerNumber){
    this.playerNumber = playerNumber;
  }

  public static void main(String[] args) {
    //try {

    NetworkClient networkClient = new NetworkClient(null, "a");

    KipifubClient player = new KipifubClient(networkClient.getMyPlayerNumber()); // 0 = rot, 1 = grün, 2 = blau

    player.nodeSpacing = 1;
    player.graph = calcNavGraph(networkClient, player.nodeSpacing);


    ColorChange colorChange;
    while(networkClient.isAlive()) {

      while ((colorChange = networkClient.pullNextColorChange()) != null) {
        //verarbeiten von colorChange
        MoveDirection nextMove = player.handleColorChange(colorChange);
        if(nextMove != null){
          networkClient.setMoveDirection(nextMove.bot, nextMove.x, nextMove.y);
        }
      }


      //networkClient.setMoveDirection(0, 1.0f, 1.0f);
      //networkClient.setMoveDirection(1, 0.0f, 1.0f);
      //networkClient.setMoveDirection(2, 1.0f, 0.0f);



    }

    //} catch (IOException e) {
    //  throw new RuntimeException("", e);
    //}
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
      //System.out.println(
      //    "colorChange: player: "+ colChange.player
      //        +", bot:"+colChange.bot
      //        +", x: "+colChange.x
      //        +", y: "+colChange.y);


      //Set/update own bots move direction
      Position currentPosition = new Position(colChange.x, colChange.y);
      //Position goal = new Position(500,500);
      return nextMoveDirection(colChange.bot, currentPosition);
    }
  }

  MoveDirection nextMoveDirection(int bot, Position currentPosition){
    Node n  = new Node(currentPosition.x/nodeSpacing, currentPosition.y/nodeSpacing);
    // check up left bottom right for walkability
   // boolean[] walkables = new boolean[4];
    Position newGoal = null;
    List<Position> possibleGoals = new ArrayList<>();

    if(isWalkableNode(new Node(n.x-1, n.y))){
      //left
      possibleGoals.add(new Position(currentPosition.x - nodeSpacing, currentPosition.y));
      //newGoal = new Position(currentPosition.x - nodeSpacing, currentPosition.y);
    } else if(isWalkableNode(new Node(n.x+1, n.y))){
      //right
      possibleGoals.add(new Position(currentPosition.x + nodeSpacing, currentPosition.y));
      //newGoal = new Position(currentPosition.x + nodeSpacing, currentPosition.y);
    } else if(isWalkableNode(new Node(n.x, n.y-1))) {
      //up
      possibleGoals.add(new Position(currentPosition.x, currentPosition.y - nodeSpacing));
      //newGoal = new Position(currentPosition.x, currentPosition.y - nodeSpacing);
    } else if(isWalkableNode(new Node(n.x, n.y+1))) {
      //down
      possibleGoals.add(new Position(currentPosition.x, currentPosition.y + nodeSpacing));
      //newGoal = new Position(currentPosition.x, currentPosition.y + nodeSpacing);
    }

    int randomIndex = (int) Math.random() * (possibleGoals.size()-1);
    System.out.println("possibleGoals: "+possibleGoals.size()+", random: "+ randomIndex);
    newGoal = possibleGoals.get(randomIndex);

    if(newGoal != null){
      return walkToGoal(bot, currentPosition, newGoal);
    } else {
      //if nothing around pos is walkable...
      return null;
    }
  }

  boolean isWalkableNode(Node node){
    if(graph[node.x][node.y] != null){
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
        if(networkClient.isWalkable(x/nodeSpacing,y/nodeSpacing)){
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
          if (graph[x - 1][y] != null) {
            graph[x][y].neighbors.add(graph[x - 1][y]);
          }
          //node right
          if (graph[x + 1][y] != null) {
            graph[x][y].neighbors.add(graph[x + 1][y]);
          }
          //node below
          if (graph[x][y + 1] != null) {
            graph[x][y].neighbors.add(graph[x][y + 1]);
          }
          //...possible to also add nodes diagonal to current node...
        }
      }
    }

    return graph;
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
