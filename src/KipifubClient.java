import lenz.htw.kipifub.ColorChange;
import lenz.htw.kipifub.net.NetworkClient;

/**
 * Created by lili on 12.06.17.
 */
public class KipifubClient {
  int playerNumber;

  KipifubClient(int playerNumber){
    this.playerNumber = playerNumber;
  }

  public static void main(String[] args){

    NetworkClient networkClient = new NetworkClient(null, "a");

    KipifubClient player = new KipifubClient(networkClient.getMyPlayerNumber()); // 0 = rot, 1 = grün, 2 = blau
    System.out.println("here 1");
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


    networkClient.setMoveDirection(0, 1.0f,1.0f);
    networkClient.setMoveDirection(1, 0.0f,1.0f);
    networkClient.setMoveDirection(2, 1.0f,0.0f);


    for(; ; ) {

    ColorChange colorChange;
    System.out.println("here 2");
    while ((colorChange = networkClient.pullNextColorChange()) != null) {
      System.out.println("here 3");
      //verarbeiten von colorChange
      player.handleColorChange(colorChange);

      networkClient.setMoveDirection(0, 1.0f,1.0f);
      networkClient.setMoveDirection(1, 0.0f,1.0f);
      networkClient.setMoveDirection(2, 1.0f,0.0f);

    }
    System.out.println("here 4");


      System.out.println("here 5");

      networkClient.setMoveDirection(0, 1.0f, 1.0f);
      networkClient.setMoveDirection(1, 0.0f, 1.0f);
      networkClient.setMoveDirection(2, 1.0f, 0.0f);

    }


  }

  void handleColorChange(ColorChange colChange){
    if(colChange.player != playerNumber){
      //update representation or something similar...

      System.out.println("other colorChange...");
      System.out.println(
          "colorChange: player: "+ colChange.player
          +", bot:"+colChange.bot
          +", x: "+colChange.x
          +", y: "+colChange.y);

    } else {
      //player is me
      System.out.println("my colorChange...");
      System.out.println(
          "colorChange: player: "+ colChange.player
              +", bot:"+colChange.bot
              +", x: "+colChange.x
              +", y: "+colChange.y);
    }
  }

  MoveDirection nextMoveDirection(Position currentPosition){
    // check up left bottom right for walkability

    // choose random direction from walkable directions

    return null;
  }


  class Position {
    int bot;
    int x;
    int y;

    Position(int bot, int x, int y){
      this.bot = bot;
      this.x = x;
      this.y = y;
    }
  }

  class MoveDirection {
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
