package com.amazonaws.lambda.demo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class QwirkleFunctionHandler implements RequestHandler<Object[], Object> {
	
    @Override
    public Object handleRequest(Object[] input, Context context) {
    	
    	
    	
    	DatabaseAccessor.loadTable();
    	
    	if(input.length == 0)return "Invalid Input Length: 0";
    	
    	if(input[0].equals("create")) {
    		//master id must be 0
    		
    		if(input.length != 3) {
    			return "Invalid Restart Input Length: " + input.length;
    		}
    		
    		double id;
    		if(input[1] instanceof Integer) {
    			id = ((Integer)input[1]).doubleValue();
    		}else {
    			id = (double)input[1];
    		}
    		
    		if(id != 0)return "Invalid Restart ID!";
    		
    		int numPlayers = (int)input[2];
    		if(numPlayers <= 0)return "Invalid Number of Players: " + numPlayers;
    		
    		String code = DatabaseAccessor.addGame(numPlayers);
    		return "Successfully created a game with " + numPlayers + " players, your code is " + code;
    	}else if(input[0].equals("place")) {  
    		//['place', gameCode, id, tiles...]
    		if(input.length < 3)return "Invalid Parameters!";
    		
    		String code = (String)input[1];
    		Item game = DatabaseAccessor.getGame(code);
    		
    		double id = (double)input[2];
    		String player = DatabaseAccessor.getPlayerByID(game, id);
    		if(player == null)return "Invalid ID!";
    		
    		
    		
    		int currentPlayer = DatabaseAccessor.getCurrentPlayer(game);
    		if(!player.substring(6).equals(String.valueOf(currentPlayer))) {
    			return "It is not your turn!";
    		}
    		
    		if(input.length < 4)return "No Tiles Provided to Place!";
    		
    		Tile[] inputTiles = new Tile[input.length - 3];
    		for(int i = 3; i < input.length; i++) {
    			if(input[i] instanceof Tile) {
    				inputTiles[i - 3] = (Tile)input[i];
    			}else if(input[i] instanceof List) {
    				inputTiles[i - 3] = getTileFromList((List)input[i]);
    			}else {
    				@SuppressWarnings("unchecked")
					LinkedHashMap<String, Object> l = ((LinkedHashMap<String, LinkedHashMap<String, Object>>)input[i]).get("map");
    				inputTiles[i - 3] = getTileFromMap(l);
    			}
    			
    		}
    		
    		
    		int result = Board.placeTiles(game, inputTiles, player);
    		if(result >= 0) {
    			int totalPlayers = DatabaseAccessor.getTotalPlayers(game);
    			DatabaseAccessor.setCurrentPlayer(game, (currentPlayer + 1) % totalPlayers);
    			
    			return DatabaseAccessor.increasePlayerScore(game, player, result);
    		}else {
    			return "Invalid Placement: " + result;
    		}
    		
    		
    	}else if(input[0].equals("connect")) {
    		
    		//['connect', gameCode, username]
    		String code = (String)input[1];
    		Item game = DatabaseAccessor.getGame(code);
    		
    		
    		//if currentPlayer == totalPlayers, already full
    		int currentConnectionIndex = DatabaseAccessor.getConnectionIndex(game);
    		if(DatabaseAccessor.getTotalPlayers(game) == currentConnectionIndex)return "Lobby Already Full!";
    		
    		if(input.length != 3)return "No Username Provided!";
    		String username = (String)input[2];
    		String player = "Player" + currentConnectionIndex;
    		
    		DatabaseAccessor.setUsername(game, player, username);
    		
    		DatabaseAccessor.setCurrentConnectingPlayer(game, currentConnectionIndex + 1);
    		
    		double nextKey = DatabaseAccessor.getIDByPlayer(game, player);
    		return nextKey;
    	}else if(input[0].equals("get")){
    		
    		//['get', gameCode, id]
    		String code = (String)input[1];
    		Item game = DatabaseAccessor.getGame(code);
    		
    		if(input.length != 3)return "Invalid Parameters!";
    		
    		double id = (double)input[2];
    		String player = DatabaseAccessor.getPlayerByID(game, id);
    		if(player == null)return "Invalid ID!";
    		
    		
    		List<Object> result = new ArrayList<>();
    		
    		result.add(getIntMapFromTile(DatabaseAccessor.getPlayerTiles(game, player)));
    		
    		
    		result.add(getIntMapFromTile(Board.getTiles(game)));
    		
    		
    		result.add(DatabaseAccessor.getScoreboard(game));
    		

    		result.add(DatabaseAccessor.getTileCounts(game));
    		
    		return result;
    	}else{
    		return "Invalid Connection Type!";
    	}
    }
	
	private Tile getTileFromMap(LinkedHashMap<String, Object> l){
        return new Tile((int)l.get("x"), (int)l.get("y"), Color.values()[(int)l.get("color")], Shape.values()[(int)l.get("shape")]);
    }
	
	private Tile getTileFromList(List<Object> l) {
		return new Tile((int)l.get(0), (int)l.get(1), Color.values()[(int)l.get(2)], Shape.values()[(int)l.get(3)]);
	}
	
	private int[][] getIntMapFromTile(List<Tile> t){
		int[][] result = new int[t.size()][];
		for(int i = 0; i < result.length; i++) {
			result[i] = getIntMapFromTile(t.get(i));
		}
		return result;
	}
	
	private int[] getIntMapFromTile(Tile t) {
		if(t == null)return new int[0];
		return new int[] {t.x, t.y, t.color.ordinal(), t.shape.ordinal()};
	}
	
	public static void main(String[] args) {
		
//		QwirkleFunctionHandler q = new QwirkleFunctionHandler();
//		
//		String create = (String)q.handleRequest(new Object[] {"create", 0.0, 2}, null);
//		String[] sp = create.split(" ");
//		String code = sp[sp.length - 1];
//		
//		double connect = (double)q.handleRequest(new Object[] {"connect", code, "Dad"}, null);
//		System.out.println(connect);
		
	}
}