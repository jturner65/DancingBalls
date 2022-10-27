package dancingBallsPKG.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * note duration types - dotted get mult by 1.5, tuples, get multiplied by (2/tuple size) -> triplets are 2/3 duration (3 in the space of 2)
 * @author 7strb
 *
 */
public enum noteDurType {
		Whole(1024),Half(512),Quarter(256),Eighth(128),Sixteenth(64),Thirtisecond(32); 
		private int value; 
		public final static String[] noteDurNames = new String[]{"Whole","Half","Quarter","Eighth","Sixteenth","Thirtisecond"};
		
		private static Map<Integer, noteDurType> map = new HashMap<Integer, noteDurType>();
	    static { for (noteDurType enumV : noteDurType.values()) { map.put(enumV.value, enumV);}}
		private noteDurType(int _val){value = _val;} 
		public int getVal(){return value;}
		/**
		 * Using given time sig denominator, return corresponding note durType
		 * @param _noteType time sig denominator
		 * @return
		 */
		public static noteDurType getDurTypeForNote(int _noteType) {
			//Find duration type based on note type.
			int newVal = Whole.value/_noteType;
			if (map.containsKey(newVal)){return getVal(newVal);}
			//if not in map, return default value
			return Quarter;
		}
		public static noteDurType getVal(int idx){return map.get(idx);}
		public static int getNumVals(){return map.size();}						//get # of values in enum
}


