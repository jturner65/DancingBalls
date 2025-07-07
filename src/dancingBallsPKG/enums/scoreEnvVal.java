package dancingBallsPKG.enums;

import java.util.HashMap;
import java.util.Map;


//desciptor of score environment variable either key signature, time signature or tempo
public enum scoreEnvVal{
    keySig,timeSig,tempo;
    private static Map<Integer, scoreEnvVal> map = new HashMap<Integer, scoreEnvVal>(); 
    static { for (scoreEnvVal enumV : scoreEnvVal.values()) { map.put(enumV.ordinal(), enumV);}}
    public int getOrdinal() {return ordinal();}     
    public static scoreEnvVal getEnumByIndex(int idx){return map.get(idx);}
    public static scoreEnvVal getEnumFromValue(int idx){return map.get(idx);}
    public static int getNumVals(){return map.size();}                        //get # of values in enum
}