package dancingBallsPKG.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * note duration types - dotted get mult by 1.5, tuples, get multiplied by (2/tuple size) -> triplets are 2/3 duration (3 in the space of 2)
 * @author John Turner
 *
 */
public enum noteDurType {
        Whole(1024),Half(512),Quarter(256),Eighth(128),Sixteenth(64),Thirtisecond(32); 
        private int value; 
        public final static String[] noteDurNames = new String[]{"Whole","Half","Quarter","Eighth","Sixteenth","Thirtisecond"};
        
        private static Map<Integer, noteDurType> valmap = new HashMap<Integer, noteDurType>();
        private static Map<Integer, noteDurType> map = new HashMap<Integer, noteDurType>(); 
        static { for (noteDurType enumV : noteDurType.values()) { valmap.put(enumV.value, enumV);map.put(enumV.ordinal(), enumV);}}
        private noteDurType(int _val){value = _val;} 
        public int getVal(){return value;}    
        public static noteDurType getEnumByIndex(int idx){return map.get(idx);}
        /**
         * Using given time sig denominator, return corresponding note durType
         * @param _noteType time sig denominator
         * @return
         */
        public static noteDurType getDurTypeForNote(int _noteType) {
            //Find duration type based on note type.
            int newVal = Whole.value/_noteType;
            if (valmap.containsKey(newVal)){return getEnumFromValue(newVal);}
            //if not in map, return default value
            return Quarter;
        }
        public static noteDurType getEnumFromValue(int idx){return valmap.get(idx);}
        public static int getNumVals(){return valmap.size();}                        //get # of values in enum
}


