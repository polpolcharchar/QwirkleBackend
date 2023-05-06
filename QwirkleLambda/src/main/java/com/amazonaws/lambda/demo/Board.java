package com.amazonaws.lambda.demo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.document.Item;

public class Board {

	private static Map<String, Tile> tileMap = new HashMap<>();
	private static List<Tile> tiles = new ArrayList<>();

	public static List<Tile> getTiles(Item game) {
		loadTiles(game);
		return tiles;
	}

	public static int placeTiles(Item game, Tile[] t, String playerName) {

		loadTiles(game);

		if (t.length == 0)
			return -2;

		if (!playerHoldsTiles(game, t, playerName))
			return -3;

		if (doTilesOverlap(t))
			return -4;

		int s = doTilesMatchAdjacent(t);
		if(s == 0)s = 1;
		
		if (s < 0)
			return s;
		
		if(s == t.length && Board.getTiles(game).size() != 0) {
			return -45;
		}

		// add tiles
		for (Tile ti : t)
			tiles.add(ti);

		// formConnections(t);

		DatabaseAccessor.removeTilesFromPlayer(game, t, playerName);

		DatabaseAccessor.addTilesToBoard(game, t, tiles);

		return s;
	}

	private static void loadTiles(Item game) {
		tiles = new ArrayList<>();
		tileMap = new HashMap<>();

		DatabaseAccessor.fillTiles(game, tiles, tileMap);
	}
	
	private static Pattern getPattern(Tile t1, Tile t2) {
		if (t1.color == t2.color && t1.shape == t2.shape)
			return null;
		if (t1.color == t2.color)
			return t1.color;
		if (t1.shape == t2.shape)
			return t1.shape;
		return null;
	}

	private static boolean doTilesOverlap(Tile[] t) {
		// tiles don't overlap each other AND no tiles are the same
		for (int i = 0; i < t.length - 1; i++) {
			for (int j = i + 1; j < t.length; j++) {
				if (t[i].x == t[j].x && t[i].y == t[j].y)
					return true;
				if (t[i].color == t[j].color && t[i].shape == t[j].shape)
					return true;
			}
		}
		return false;
	}

	private static Pattern getOppositePattern(Pattern base, Tile input) {
		if (base instanceof Color)
			return input.shape;
		return input.color;
	}

	private static Pattern doTilesHavePattern(Tile[] t) {
		// tiles form a pattern with each other:
		// if they have no similarities, return false
		if (t.length == 2) {
			Pattern duo = getPattern(t[0], t[1]);
			if (duo == null)
				return null;

			return getPattern(t[0], t[1]);
		} else if (t.length > 2) {
			for (int i = 0; i < t.length - 2; i++) {
				Pattern a = getPattern(t[i], t[i + 1]);
				Pattern b = getPattern(t[i + 1], t[i + 2]);
				if (a == null || b == null || a != b)
					return null;
			}
			return getPattern(t[0], t[1]);
		}
		return null;
	}

	private static int getOppositePatternSize(Pattern p) {
		if (p instanceof Color)
			return Shape.values().length;
		return Color.values().length;
	}

	private static int doTilesMatchAdjacent(Tile[] t) {

		Axis a = doTilesFormConinuousLine(t);
		Pattern p = doTilesHavePattern(t);
		if (a == null)
			return -10;
		if (p == null && t.length != 1)
			return -11;

		int score = t.length;

		if (t.length == 1) {
			score--;
			Set<Pattern> h = new HashSet<>();
			Set<Pattern> v = new HashSet<>();

			Pattern pHorizontal = null;
			Pattern pVertical = null;

			for (Tile next : tiles) {
				// solo logic
				if (next.y == t[0].y && next.x + 1 == t[0].x) {
					// next to the west = t[0]
					if (pHorizontal == null) {
						score++;
						Pattern tempH = getPattern(t[0], next);
						if (tempH == null)
							return -12;
						pHorizontal = tempH;

						h.add(getOppositePattern(pHorizontal, t[0]));
					}

					Tile west = next;
					Tile last = t[0];

					while (west != null) {
						score++;
						if (!h.add(getOppositePattern(pHorizontal, west))) {
							return -13;
						}
						if (getPattern(last, west) != pHorizontal)
							return -14;

						// west = west.west;
						west = getWestTile(west);

						if (last == t[0])
							last = next;
						// else last = last.west;
						else
							last = getWestTile(last);
					}
				} else if (next.y == t[0].y && next.x - 1 == t[0].x) {
					// next to east of t[0]

					if (pHorizontal == null) {
						score++;
						Pattern tempH = getPattern(t[0], next);
						if (tempH == null)
							return -15;
						pHorizontal = tempH;

						h.add(getOppositePattern(pHorizontal, t[0]));
					}
					Tile east = next;
					Tile last = t[0];

					while (east != null) {
						score++;
						if (!h.add(getOppositePattern(pHorizontal, east))) {
							return -16;
						}
						if (getPattern(last, east) != pHorizontal)
							return -17;

						// east = east.east;
						east = getEastTile(east);

						if (last == t[0])
							last = next;
						// else last = last.east;
						else
							last = getEastTile(last);
					}
				} else if (next.x == t[0].x && next.y + 1 == t[0].y) {
					// next to north of t[0]
					if (pVertical == null) {
						score++;
						Pattern tempV = getPattern(t[0], next);
						if (tempV == null)
							return -18;
						pVertical = tempV;

						v.add(getOppositePattern(pVertical, t[0]));
					}
					Tile north = next;
					Tile last = t[0];

					while (north != null) {
						score++;
						if (!v.add(getOppositePattern(pVertical, north))) {
							return -19;
						}
						if (getPattern(last, north) != pVertical)
							return -20;

						// north = north.north;
						north = getNorthTile(north);

						if (last == t[0])
							last = next;
						// else last = last.north;
						else
							last = getNorthTile(last);
					}
				} else if (next.x == t[0].x && next.y - 1 == t[0].y) {
					// next to south of t[0]

					if (pVertical == null) {
						score++;
						Pattern tempV = getPattern(t[0], next);
						if (tempV == null)
							return -21;
						pVertical = tempV;

						v.add(getOppositePattern(pVertical, t[0]));
					}
					Tile south = next;
					Tile last = t[0];

					while (south != null) {
						score++;
						if (!v.add(getOppositePattern(pVertical, south))) {
							return -22;
						}
						if (getPattern(last, south) != pVertical)
							return -23;

						// south = south.south;
						south = getSouthTile(south);

						if (last == t[0])
							last = next;
						// else last = last.south;
						else
							last = getSouthTile(last);
					}
				}
			}
			if (v.size() == getOppositePatternSize(pVertical))
				score += v.size();
			if (h.size() == getOppositePatternSize(pHorizontal))
				score += h.size();
		} else if (a == Axis.HORIZONTAL) {
			// TILES ARE HORIZONTAL
			Set<Pattern> h = new HashSet<>();
			for (Tile ti : t) {
				h.add(getOppositePattern(p, ti));
			}

			// look at horizontal end pieces of t
			// t is already sorted so this is t[0] and t[len - 1]
			// look for west of 0 and east of len - 1

			for (Tile next : tiles) {
				if (next.y == t[0].y && next.x + 1 == t[0].x) {
					// tile to the west of t[0]
					Tile west = next;
					Tile last = t[0];
					while (west != null) {
						score++;
						if (!h.add(getOppositePattern(p, west))) {
							return -24;
						}
						if (getPattern(west, last) != p)
							return -25;

						// west = west.west;
						west = getWestTile(west);

						if (last == t[0])
							last = next;
						// else last = last.west;
						else
							last = getWestTile(last);
					}
				} else if (next.y == t[t.length - 1].y && next.x - 1 == t[t.length - 1].x) {
					// tile to east of t[len - 1]
					Tile east = next;
					Tile last = t[t.length - 1];
					while (east != null) {
						score++;
						if (!h.add(getOppositePattern(p, east))) {
							return -26;
						}
						if (getPattern(east, last) != p)
							return -27;

						// east = east.east;
						east = getEastTile(east);

						if (last == t[t.length - 1])
							last = next;
						// else last = last.east;
						else
							last = getEastTile(last);
					}
				}
			}

			for (Tile ti : t) {
				// check for verticality
				Set<Pattern> v = new HashSet<>();
				Pattern pVertical = null;

				for (Tile next : tiles) {
					if (next.x == ti.x && next.y + 1 == ti.y) {
						// next to north of ti
						if (pVertical == null) {
							score++;
							Pattern tempV = getPattern(ti, next);
							if (tempV == null)
								return -28;
							pVertical = tempV;

							v.add(getOppositePattern(pVertical, ti));
						}

						Tile last = ti;
						Tile north = next;
						while (north != null) {
							score++;
							if (!v.add(getOppositePattern(pVertical, north))) {
								return -29;
							}
							if (getPattern(last, north) != pVertical)
								return -30;

							// north = north.north;
							north = getNorthTile(north);

							if (last == ti)
								last = next;
							// else last = last.north;
							else
								last = getNorthTile(last);
						}

					} else if (next.x == ti.x && next.y - 1 == ti.y) {
						// next to south of ti

						if (pVertical == null) {
							score++;
							Pattern tempV = getPattern(ti, next);
							if (tempV == null)
								return -31;
							pVertical = tempV;

							v.add(getOppositePattern(pVertical, ti));
						}

						Tile last = ti;
						Tile south = next;
						while (south != null) {
							score++;
							if (!v.add(getOppositePattern(pVertical, south))) {
								return -32;
							}
							if (getPattern(last, south) != pVertical)
								return -33;

							// south = south.south;
							south = getSouthTile(south);

							if (last == ti)
								last = next;
							// else last = last.south;
							else
								last = getSouthTile(last);
						}

					}
				}
				if (v.size() == getOppositePatternSize(pVertical))
					score += v.size();
			}
			if (h.size() == getOppositePatternSize(p))
				score += h.size();
		} else {
			// TILES ARE VERTICAL
			Set<Pattern> v = new HashSet<>();
			for (Tile ti : t) {
				v.add(getOppositePattern(p, ti));
			}

			for (Tile next : tiles) {
				if (next.x == t[0].x && next.y + 1 == t[0].y) {
					// tile to the north of t[0]
					Tile north = next;
					Tile last = t[0];
					while (north != null) {
						score++;
						if (!v.add(getOppositePattern(p, north))) {
							return -34;
						}
						if (getPattern(north, last) != p)
							return -35;

						// north = north.north;
						north = getNorthTile(north);

						if (last == t[0])
							last = next;
						// else last = last.north;
						else
							last = getNorthTile(last);
					}
				} else if (next.x == t[t.length - 1].x && next.y - 1 == t[t.length - 1].y) {
					// tile to south of t[len - 1]
					Tile south = next;
					Tile last = t[t.length - 1];
					while (south != null) {
						score++;
						if (!v.add(getOppositePattern(p, south))) {
							return -36;
						}
						if (getPattern(south, last) != p)
							return -37;

						// south = south.south;
						south = getSouthTile(south);

						if (last == t[t.length - 1])
							last = next;
						// else last = last.south;
						else
							last = getSouthTile(last);
					}
				}
			}

			for (Tile ti : t) {
				Set<Pattern> h = new HashSet<>();
				Pattern pHorizontal = null;

				for (Tile next : tiles) {
					if (next.y == ti.y && next.x + 1 == ti.x) {
						// next to west of ti
						if (pHorizontal == null) {
							score++;
							Pattern tempV = getPattern(ti, next);
							if (tempV == null)
								return -38;
							pHorizontal = tempV;

							h.add(getOppositePattern(pHorizontal, ti));
						}

						Tile last = ti;
						Tile west = next;

						// System.out.println("Horizontal Pattern: " + pHorizontal);

						while (west != null) {
							score++;
							if (!h.add(getOppositePattern(pHorizontal, west))) {
								return -39;
							}
							// System.out.println("Last: " + last + ", west: " + west + ", pattern: " +
							// getPattern(last, west));
							if (getPattern(last, west) != pHorizontal)
								return -40;

							// west = west.west;
							west = getWestTile(west);

							if (last == ti)
								last = next;
							// else last = last.west;
							else
								last = getWestTile(last);
						}

					} else if (next.y == ti.y && next.x - 1 == ti.x) {
						// next to east of ti
						if (pHorizontal == null) {
							score++;
							Pattern tempV = getPattern(ti, next);
							if (tempV == null)
								return -41;
							pHorizontal = tempV;

							h.add(getOppositePattern(pHorizontal, ti));
						}

						Tile last = ti;
						Tile east = next;
						while (east != null) {
							score++;
							if (!h.add(getOppositePattern(pHorizontal, east))) {
								return -42;
							}
							if (getPattern(last, east) != pHorizontal)
								return -43;

							// east = east.east;
							east = getEastTile(east);

							if (last == ti)
								last = next;
							// else last = last.east;
							else
								last = getEastTile(last);
						}

					}
				}
				if (h.size() == getOppositePatternSize(pHorizontal))
					score += h.size();
			}
			if (v.size() == getOppositePatternSize(p))
				score += v.size();
		}

		return score;
	}

	private static boolean playerHoldsTiles(Item game, Tile[] input, String playerName) {

		List<Tile> playerTiles = DatabaseAccessor.getPlayerTiles(game, playerName);

		boolean[] seen = new boolean[playerTiles.size()];
		for (Tile t : input) {
			boolean found = false;

			for (int i = 0; i < playerTiles.size(); i++) {
				if (!seen[i] && playerTiles.get(i).color == t.color && playerTiles.get(i).shape == t.shape) {
					seen[i] = true;
					found = true;
					break;
				}
			}

			if (!found)
				return false;
		}

		return true;
	}

	private static Axis doTilesFormConinuousLine(Tile[] t) {
		// check that tiles form a single line:

		// loop over, check if x changes or y changes
		boolean xChange = false;
		boolean yChange = false;
		for (int i = 1; i < t.length; i++) {
			if (t[i].x != t[i - 1].x)
				xChange = true;
			if (t[i].y != t[i - 1].y)
				yChange = true;
		}
		// if diagonal, return false
		if (xChange && yChange)
			return null;

		// sort by the changing location variable
		// loop to make sure it only ever increments by one
		if (xChange) {
			Arrays.sort(t, (a, b) -> a.x - b.x);
			for (int i = 1; i < t.length; i++) {
				if (t[i - 1].x + 1 != t[i].x)
					return null;
			}
			return Axis.HORIZONTAL;
		} else {
			Arrays.sort(t, (a, b) -> a.y - b.y);
			for (int i = 1; i < t.length; i++) {
				if (t[i - 1].y + 1 != t[i].y)
					return null;
			}
			return Axis.VERTICAL;
		}
	}

	private static Tile getNorthTile(Tile t) {
		return tileMap.getOrDefault(t.x + "," + (t.y - 1), null);
	}

	private static Tile getSouthTile(Tile t) {
		return tileMap.getOrDefault(t.x + "," + (t.y + 1), null);
	}

	private static Tile getWestTile(Tile t) {
		return tileMap.getOrDefault((t.x - 1) + "," + t.y, null);
	}

	private static Tile getEastTile(Tile t) {
		return tileMap.getOrDefault((t.x + 1) + "," + t.y, null);
	}
}

class Tile{
	Color color;
	Shape shape;
	
	int x;
	int y;
	
	Tile(int x, int y, Color c, Shape s){
		this.x = x;
		this.y = y;
		this.color = c;
		this.shape = s;
	}
	Tile(Color c, Shape s){
		this.x = -1;
		this.y = -1;
		this.color = c;
		this.shape = s;
	}
	Tile(Map<String, BigDecimal> m){
		this.x = m.get("x").intValue();
		this.y = m.get("y").intValue();
		this.color = Color.values()[m.get("color").intValue()];
		this.shape = Shape.values()[m.get("shape").intValue()];
	}
	
	static Tile random() {
		return new Tile(Color.values()[(int)(Math.random() * Color.values().length)], Shape.values()[(int)(Math.random() * Shape.values().length)]);
	}

	Map<String, Integer> getMap(){
		Map<String, Integer> m = new HashMap<>();
		m.put("x", x);
		m.put("y", y);
		m.put("color", color.ordinal());
		m.put("shape", shape.ordinal());
		return m;
	}
	
	@Override
	public String toString() {
		return color + " " + shape + " at (" + x + ", " + y + ")";
	}
}

interface Pattern{
	
}
enum Color implements Pattern{
	RED,
	ORANGE,
	YELLOW,
	GREEN,
	BLUE,
	PURPLE;
}
enum Shape implements Pattern{
	CIRCLE,
	CLOVER,
	DIAMOND,
	SQUARE,
	STAR,
	X;
}

enum Axis{
	HORIZONTAL, VERTICAL;
}
