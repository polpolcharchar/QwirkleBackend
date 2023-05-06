package com.amazonaws.lambda.demo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.NameMap;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;


public class DatabaseAccessor {

	static Table table;

	public static void loadTable() {
		AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().withRegion(Regions.US_WEST_2).build();
		DynamoDB dynamoDB = new DynamoDB(client);

		Table tableResult = dynamoDB.getTable("Qwirkle");
		tableResult.describe();
		table = tableResult;
	}

	private static Item getDefaultGame(int numPlayers, String name) {
		Item game = new Item().withPrimaryKey("Name", name);
		
		
		// create the board first
		List<Map<String, Integer>> boardTiles = new ArrayList<>();
		
		game.withList("Board_Tiles", boardTiles);
		game.withInt("Round", 0);
		game.withInt("Total Players", numPlayers);
		game.withInt("Current Connecting Player", 0);
		game.withInt("Current Player", 0);
		
//		Item b = new Item()/*.withPrimaryKey("Name", "Board")*/.withList("Tiles", boardTiles).withInt("Round", 0)
//				.withInt("Total Players", numPlayers).withInt("Current Connecting Player", 0)
//				.withInt("Current Player", 0);

		// add the board
//		table.putItem(b);
//		game.with("Board", b);

		// create the tilepool and shuffle
		List<Map<String, Integer>> tilePool = new ArrayList<>();
		for (Color c : Color.values()) {
			for (Shape s : Shape.values()) {
				for (int n = 0; n < 3; n++) {
					tilePool.add(new Tile(c, s).getMap());
				}
			}
		}
		Collections.shuffle(tilePool);
		int totalTiles = tilePool.size();

		// dont add pool yet because players' default tiles will take from it

		// create players
		for (int i = 0; i < numPlayers; i++) {
			List<Map<String, Integer>> playerTiles = new ArrayList<>();
			for (int n = 0; n < 7; n++) {
				if (tilePool.size() != 0) {
					// pull from created tilePool
					playerTiles.add(tilePool.remove(0));
				}
			}

			double id = Math.random();

			// add player
//			Item p = new Item()/*.withPrimaryKey("Name", "Player" + i)*/.withList("Tiles", playerTiles).withInt("Score", 0)
//					.withDouble("ID", id).withString("Username", "Player" + i);
//			table.putItem(p);
//			game.with("Player" + i, p);
			
			String playerIdentifier = "Player" + i;
			
			game.withList(playerIdentifier + "_Tiles", playerTiles);
			game.withInt(playerIdentifier + "_Score", 0);
			game.withDouble(playerIdentifier + "_ID", id);
			game.withString(playerIdentifier + "_Username", playerIdentifier);
		}

		// add pool now
//		Item p = new Item()/*.withPrimaryKey("Name", "Pool")*/.withList("Tiles", tilePool)
//				.withInt("Tiles Remaining", tilePool.size()).withInt("Total Tiles", totalTiles);
//		table.putItem(p);
//		game.with("Pool", p);
		
		game.withList("Pool_Tiles", tilePool);
		game.withInt("Tiles Remaining", tilePool.size());
		game.withInt("Total Tiles", totalTiles);

		return game;
	}
	
	public static String addGame(int numPlayers) {		
		if(table.getItem("Name", "LobbyInfo") == null) {
			table.putItem(new Item()
			.withPrimaryKey("Name", "LobbyInfo")
			.withInt("NextBoardIndex", 0));
		}
		
		String code = getRandomCode();
		
		Item g = getDefaultGame(numPlayers, code);
		table.putItem(g);
		
		return code;
	}
	
	private static String getRandomCode() {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < 5; i++) {
			sb.append((char) ('a' + Math.random() * ('z' - 'a' + 1)));//chatgpt
		}
		return sb.toString();
	}

	public static Item getGame(String code) {
		Item g = table.getItem("Name", code);
		if(g == null)throw new RuntimeException("That Game Doesn't Exist!");
		return g;
	}
	
	
	public static List<Tile> getNextNTilesFromPool(Item game, int n) {
		
		List<Tile> result = new ArrayList<>();
		
		List<Map<String, BigDecimal>> tiles = game.getList("Pool_Tiles");
		for(int i = 0; i < Math.min(n, game.getInt("Tiles Remaining")); i++) {
			result.add(new Tile(tiles.remove(0)));
		}
		
		game = game
				.withList("Pool_Tiles", tiles)
				.withInt("Tiles Remaining", tiles.size());
		table.putItem(game);
		
		return result;
	}

 	public static List<Tile> getPlayerTiles(Item game, String playerIdentifier) {
 		
 		List<Map<String, BigDecimal>> items = game.getList(playerIdentifier + "_Tiles");
 		

		List<Tile> result = new ArrayList<>();
		for (Map<String, BigDecimal> m : items) {
			result.add(new Tile(m));
		}

		return result;
	}

	public static void addTilesToBoard(Item game, Tile[] input, List<Tile> oldTiles) {
		List<Map<String, Integer>> newTileList = new ArrayList<>();
		for (Tile t : oldTiles)
			newTileList.add(t.getMap());
		

		UpdateItemSpec u = new UpdateItemSpec().withPrimaryKey("Name", game.getString("Name")).withUpdateExpression("set #p = :val1")
				.withNameMap(new NameMap().with("#p", "Board_Tiles"))
				.withValueMap(new ValueMap().withList(":val1", newTileList));

		table.updateItem(u);
	}

	public static void removeTilesFromPlayer(Item game, Tile[] input, String playerIdentifier) {
		

		
		List<Map<String, BigDecimal>> playerTileMap = game.getList(playerIdentifier + "_Tiles");

		// Load the tiles this player holds into a list
		List<Tile> playerTiles = new ArrayList<>();
		for (Map<String, BigDecimal> m : playerTileMap) {
			playerTiles.add(new Tile(m));
		}

		// Remove
		for (Tile t : input) {
			for (int i = 0; i < playerTiles.size(); i++) {
				if (playerTiles.get(i).color == t.color && playerTiles.get(i).shape == t.shape) {
					playerTiles.remove(i);
					break;
				}
			}
		}

		// Refill with random tiles
		//while (playerTiles.size() < 7)
			//playerTiles.add(Tile.random());
		playerTiles.addAll(getNextNTilesFromPool(game, 7 - playerTiles.size()));

		List<Map<String, Integer>> dataMap = new ArrayList<>();
		for (Tile t : playerTiles)
			dataMap.add(t.getMap());

		UpdateItemSpec u = new UpdateItemSpec().withPrimaryKey("Name", game.getString("Name"))
				.withUpdateExpression("set #p = :val1").withNameMap(new NameMap().with("#p", playerIdentifier + "_Tiles"))
				.withValueMap(new ValueMap().withList(":val1", dataMap));

		table.updateItem(u);

	}

	public static int getTotalPlayers(Item game) {
		return game.getInt("Total Players");
	}
	
	public static int getTilesRemaining(Item game) {
		return game.getInt("Tiles Remaining");
	}
	
	public static int[] getTileCounts(Item game) {
		return new int[] {getTilesRemaining(game), getTotalTiles(game)};
	}
	
	public static int getTotalTiles(Item game) {
		return game.getInt("Total Tiles");
	}

	public static int getConnectionIndex(Item game) {
		return game.getInt("Current Connecting Player");
	}

	
	public static void setCurrentConnectingPlayer(Item game, int i) {
		
		
		UpdateItemSpec u = new UpdateItemSpec().withPrimaryKey("Name", game.getString("Name")).withUpdateExpression("set #p = :val1")
				.withNameMap(new NameMap().with("#p", "Current Connecting Player"))
				.withValueMap(new ValueMap().withInt(":val1", i));
		table.updateItem(u);
	}
	
	public static void setUsername(Item game, String playerIdentifier, String username) {
		
		
		UpdateItemSpec u = new UpdateItemSpec().withPrimaryKey("Name", game.getString("Name"))
				.withUpdateExpression("set #p = :val1")
				.withNameMap(new NameMap()
				.with("#p", playerIdentifier + "_Username"))
				.withValueMap(new ValueMap().withString(":val1", username));
		table.updateItem(u);
	}

	public static String getUsername(Item game, String playerIdentifier) {
		
		
		return game.getString(playerIdentifier + "_Username");
	}
	
	public static Object[][] getScoreboard(Item game){		
		int n = getConnectionIndex(game);
		
		int currentPlayer = getCurrentPlayer(game);
		
		Object[][] result = new Object[n][];
		for(int i = 0; i < n; i++) {
			String player = "Player" + i;
			result[i] = new Object[] {getUsername(game, player), getPlayerScore(game, player), currentPlayer == i};
		}
		
		return result;
	}
	
	public static int getCurrentPlayer(Item game) {		
		return game.getInt("Current Player");
	}

	
	public static void setCurrentPlayer(Item game, int i) {
		
		UpdateItemSpec u = new UpdateItemSpec().withPrimaryKey("Name", game.getString("Name")).withUpdateExpression("set #p = :val1")
				.withNameMap(new NameMap().with("#p", "Current Player"))
				.withValueMap(new ValueMap().withInt(":val1", i));
		table.updateItem(u);
	}

	public static int increasePlayerScore(Item game, String playerIdentifier, int score) {
		
		
		int newScore = getPlayerScore(game, playerIdentifier) + score;

		UpdateItemSpec u = new UpdateItemSpec().withPrimaryKey("Name", game.getString("Name")).withUpdateExpression("set #p = :val1")
				.withNameMap(new NameMap().with("#p", playerIdentifier + "_Score")).withValueMap(new ValueMap().withInt(":val1", newScore));
		table.updateItem(u);

		return newScore;
	}

	public static int getPlayerScore(Item game, String playerIdentifier) {
		
		
		return game.getInt(playerIdentifier + "_Score");
	}

	public static String getPlayerByID(Item game, double id) {
		
		
		int numPlayers = game.getInt("Total Players");
		for (int i = 0; i < numPlayers; i++) {
			if (table.getItem("Name", game.getString("Name")).getDouble(("Player" + i) + "_ID") == id) {
				return "Player" + i;
			}
		}
		return null;
	}

	public static double getIDByPlayer(Item game, String playerIdentifier) {
				
		return game.getDouble(playerIdentifier + "_ID");
	}

	public static void fillTiles(Item game, List<Tile> l, Map<String, Tile> m) {
		
		
		List<Map<String, BigDecimal>> items = game.getList("Board_Tiles");
        
        for(Map<String, BigDecimal> item : items) {
        	Tile t = new Tile(item);
        	
        	l.add(t);
        	m.put(t.x + "," + t.y, t);
        }
	}
}
