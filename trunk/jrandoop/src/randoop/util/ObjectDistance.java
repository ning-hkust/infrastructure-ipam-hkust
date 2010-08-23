package randoop.util;

import java.util.ArrayList;

import java.io.IOException;
import java.lang.reflect.Field;


public class ObjectDistance {
	// TODO (JJ): add array distance
	public static int compare(Object x, Object y) {
		int ret = 0;
		if (y instanceof Boolean) {
			if ((Boolean) x && !(Boolean) y)
				ret = 1;
			else if (!(Boolean) x && (Boolean) y)
				ret = -1;
			else
				ret = 0;
		}

		if (y instanceof Integer) {

			int tmp = (Integer) x - (Integer) y;
			if (tmp > 0)
				ret = 1;
			else if (tmp < 0)
				ret = -1;
			else
				ret = 0;
		}

		if (y instanceof Long) {
			Long tmp = (Long) x - (Long) y;
			if (tmp > 0)
				ret = 1;
			else if (tmp < 0)
				ret = -1;
			else
				ret = 0;
		}

		if (y instanceof Float) {
			Float tmp = (Float) x - (Float) y;
			if (tmp > 0)
				ret = 1;
			else if (tmp < 0)
				ret = -1;
			else
				ret = 0;
		}
		if (y instanceof Double) {
			Double tmp = (Double) x - (Double) y;
			if (tmp > 0)
				ret = 1;
			else if (tmp < 0)
				ret = -1;
			else
				ret = 0;
		}

		if (y instanceof Byte) {
			Integer tmp = (Integer) x - (Integer) y;
			if (tmp > 0)
				ret = 1;
			else if (tmp < 0)
				ret = -1;
			else
				ret = 0;
		}

		if (y instanceof Character) {
			char tmp = (char) ((Character) x - (Character) y);
			if (tmp > 0)
				ret = 1;
			else if (tmp < 0)
				ret = -1;
			else
				ret = 0;
		}
		/*
		 * 
		 * 
		 * if (y instanceof String) ret = GetDistance((String)x, (String)y);
		 */
		return ret;
	}

	public static double GetDistanceObjectArray(Object x, Object y) {
		// 0.0 (size same and type same)
		// 0.2 (size same and type comp)
		// 0.4 (size diff and type same)
		// 0.6 (size diff and type comp)
		// 0.8 (size same and type diff)
		// 1.0 (size diff and type diff)
		// compatible : 0.7 (size different and type
		// different : 1
		if (x == null || y == null)
			if (x == null && y == null)
				return 0;
			else
				return 1;

		boolean sizesame = false;

		int xlen = java.lang.reflect.Array.getLength(x);
		int ylen = java.lang.reflect.Array.getLength(y);

		if (xlen == ylen)
			sizesame = true;

		Class<?> eleX = x.getClass().getComponentType();
		Class<?> eleY = y.getClass().getComponentType();

		boolean typesame = false;
		boolean typecomp = false;
		if (eleX.equals(eleY)) {
			typesame = true;
		} else if (Reflection.canBeUsedAs(eleX, eleY))
			typecomp = true;

		boolean typediff = !(typesame == typecomp);

		if (sizesame && typesame)
			return 0.0;// 0.0 (size same and type same)
		if (sizesame && typecomp)
			return 0.2;// 0.2 (size same and type comp)
		if (!sizesame && typesame)
			return 0.4;// 0.4 (size diff and type same)
		if (!sizesame && typecomp)
			return 0.6;// 0.6 (size diff and type comp)
		if (sizesame && typediff)
			return 0.8;// 0.8 (size same and type diff)
		if (!sizesame && typediff)
			return 1.0;// 1.0 (size diff and type diff)

		return 1;
	}

	public static double GetDistanceObjectSimple(Object x, Object y) {
		// identical : 0
		// same : 0.4
		// compatible : 0.7
		// different : 1
		try {
			if (x == null || y == null)
				if (x == null && y == null)
					return 0;
				else
					return 1;

			if (x.getClass().equals(y.getClass())) {
				if (x.equals(y))
					return 0;
				else
					return 0.4;
			}

			if (Reflection.canBeUsedAs(x.getClass(), y.getClass()))
				return 0.7;
		} catch (Exception e) {
			return 1;
		}

		return 1;
	}

	public static double GetDistanceObjectARTOO(Object x, Object y) {
		return GetDistanceObjectARTOO(x, y, 0);
	}

	public static double GetDistanceObjectARTOO(Object x, Object y, int depth) {
		double ed = 10, td = 10, fd = 10, pl;

		if (depth == 2)
			return 0;

		if (x == null || y == null)
			if (x == null && y == null)
				return 0;
			else
				return 10;

		if (x.equals(y)) {
			if (depth == 0 && PrimitiveTypes.isBoxedOrPrimitiveOrStringType(x.getClass())
					&& PrimitiveTypes.isBoxedOrPrimitiveOrStringType(y.getClass())) {
				ed = GetDistancePrimitive(x, y); // elementary_distance
			} else
				ed = 1;

		} else if (x.equals(void.class) || y.equals(void.class))
			ed = 1;

		else
			ed = 0.1;
		// td = type_distance(x,y);
		fd = field_distance(x, y, depth);

		pl = pathLength(x.getClass(), y.getClass());

		return norm(ed) + norm(fd) + norm(pl);
		// return 0;
	}

	private static double norm(double val) {
		return 1 - 1 / (1 + val);
	}

	private static double field_distance(Object x, Object y, int depth) {
		Field[] fields_x = x.getClass().getFields();
		Field[] fields_y = y.getClass().getFields();

		int nonshared_cnt = 0;

		for (Field fx : fields_x) {
			for (Field fy : fields_y) {
				if (fx == fy) {
					// shared
					GetDistanceObjectARTOO(x, y, depth + 1);
				} else
					nonshared_cnt++;

			}
		}

		return nonshared_cnt;
	}

	private static double pathLength(Class<?> x, Class<?> y) {
		ArrayList<Class<?>> superClassX = new ArrayList<Class<?>>();

		if (x.equals(y))
			return 0;

		Class<?> tmp;
		while (true) {
			tmp = x.getSuperclass();
			if (tmp == null)
				break;
			x = tmp;
			superClassX.add(tmp);
		}

		int ydist = 0;

		while (true) {

			for (int xdist = 0; xdist < superClassX.size(); xdist++) {
				if (y.getName().equals(superClassX.get(xdist).getName()))
					return xdist + ydist;
			}
			tmp = y.getSuperclass();
			if (tmp == null)
				break;
			y = tmp;
			ydist++;
		}

		return 10;
	}

	public static double GetDistancePrimitive(Object x, Object y) {
		if (y instanceof Boolean)
			return (x == y) ? 0.0 : 1.0;

		if (y instanceof Integer) {
			int xx = (Integer) x;
			if (x == null)
				xx = 0;
			int yy = (Integer) y;
			int ret = (int) (xx - yy);
			if (ret < 0)
				ret = (int) -ret;
			if (ret > Integer.MAX_VALUE / 2)
				ret = Integer.MAX_VALUE - ret;
			return (double) ret;
		}

		if (y instanceof Short) {
			short xx = (Short) x;
			if (x == null)
				xx = 0;
			short yy = (Short) y;
			short ret = (short) (xx - yy);
			if (ret < 0)
				ret = (short) -ret;
			if (ret > Short.MAX_VALUE / 2)
				ret = (short) (Short.MAX_VALUE - ret);
			return (double) ret;
		}

		if (y instanceof Long) {
			long xx = (Long) x;
			if (x == null)
				xx = 0;
			long yy = (Long) y;
			long ret = (long) (xx - yy);
			if (ret < 0)
				ret = (long) -ret;
			if (ret > Long.MAX_VALUE / 2)
				ret = Long.MAX_VALUE - ret;
			return (double) ret;
		}

		if (y instanceof Float) {
			float xx = (Float) x;
			if (x == null)
				xx = 0;
			float yy = (Float) y;
			float ret = (float) (xx - yy);
			if (ret < 0)
				ret = (float) -ret;
			if (ret > Float.MAX_VALUE / 2)
				ret = Float.MAX_VALUE - ret;
			return (double) ret;
		}

		if (y instanceof Double) {
			double xx = (Double) x;
			if (x == null)
				xx = 0;
			double yy = (Double) y;
			double ret = (double) (xx - yy);
			if (ret < 0)
				ret = (double) -ret;
			if (ret > Double.MAX_VALUE / 2)
				ret = Double.MAX_VALUE - ret;
			return (double) ret;
		}
		if (y instanceof Byte) {
			byte xx = (Byte) x;
			if (x == null)
				xx = 0;
			byte yy = (Byte) y;
			byte ret = (byte) (xx - yy);
			if (ret < 0)
				ret = (byte) -ret;
			if (ret > Byte.MAX_VALUE / 2)
				ret = (byte) (Byte.MAX_VALUE - ret);
			return (double) ret;
		}

		if (y instanceof String) {
			int ret = getStringDistance((String) x, (String) y);
			return (double) ret;
		}

		return 0.0;
	}

	public static int getStringDistance(String s, String t) {
		if (s == null)
			s = "";
		if (t == null)
			t = "";
		// if (s == null || t == null) {
		// throw new IllegalArgumentException("Strings must not be null");
		// }

		/*
		 * The difference between this impl. and the previous is that, rather
		 * than creating and retaining a matrix of size s.length()+1 by
		 * t.length()+1, we maintain two single-dimensional arrays of length
		 * s.length()+1. The first, d, is the 'current working' distance array
		 * that maintains the newest distance cost counts as we iterate through
		 * the characters of String s. Each time we increment the index of
		 * String t we are comparing, d is copied to p, the second int[]. Doing
		 * so allows us to retain the previous cost counts as required by the
		 * algorithm (taking the minimum of the cost count to the left, up one,
		 * and diagonally up and to the left of the current cost count being
		 * calculated). (Note that the arrays aren't really copied anymore, just
		 * switched...this is clearly much better than cloning an array or doing
		 * a System.arraycopy() each time through the outer loop.)
		 * 
		 * Effectively, the difference between the two implementations is this
		 * one does not cause an out of memory condition when calculating the LD
		 * over two very large strings.
		 */

		int n = s.length(); // length of s
		int m = t.length(); // length of t

		if (n == 0) {
			return m;
		} else if (m == 0) {
			return n;
		}

		int p[] = new int[n + 1]; // 'previous' cost array, horizontally
		int d[] = new int[n + 1]; // cost array, horizontally
		int _d[]; // placeholder to assist in swapping p and d

		// indexes into strings s and t
		int i; // iterates through s
		int j; // iterates through t

		char t_j; // jth character of t

		int cost; // cost

		for (i = 0; i <= n; i++) {
			p[i] = i;
		}

		for (j = 1; j <= m; j++) {
			t_j = t.charAt(j - 1);
			d[0] = j;

			for (i = 1; i <= n; i++) {
				cost = s.charAt(i - 1) == t_j ? 0 : 1;
				// minimum of cell to the left+1, to the top+1, diagonally left
				// and up +cost
				d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
			}

			// copy current distance counts to 'previous row' distance counts
			_d = p;
			p = d;
			d = _d;
		}

		// our last action in the above loop was to switch d and p, so p now
		// actually has the most recent cost counts
		return p[n];
	}

}
