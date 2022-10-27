package dancingBallsPKG.enums;

import java.util.HashMap;
import java.util.Map;

//desciptor of score environment variable either key signature, time signature or tempo
public enum scoreEnvVal{
	keySig(0),timeSig(1),tempo(2);
	private int value; 
	private static Map<Integer, scoreEnvVal> map = new HashMap<Integer, scoreEnvVal>(); 
	static { for (scoreEnvVal enumV : scoreEnvVal.values()) { map.put(enumV.value, enumV);}}
	private scoreEnvVal(int _val){value = _val;} 
	public int getVal(){return value;} 	
	public static scoreEnvVal getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum
}