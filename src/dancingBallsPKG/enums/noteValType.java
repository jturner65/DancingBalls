package dancingBallsPKG.enums;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * note and key value
 * @author John Turner
 *
 */
public enum noteValType {
	C(0),Cs(1),D(2),Ds(3),E(4),F(5),Fs(6),G(7),Gs(8),A(9),As(10),B(11),rest(12); 
	
	public static final noteValType[] wKeyVals = new noteValType[] {C, B, A, G, F,E,D};
	public static final noteValType[] bKeyVals = new noteValType[] {As, Gs, Fs, Ds, Cs};	
	private int value; 
	private static Map<Integer, noteValType> valMap = new HashMap<Integer, noteValType>(); 
	//array of naturals decreased by a sharp in y on grid
	private static Map<noteValType, Boolean> hasSharps = new HashMap<noteValType, Boolean>();
	//array of naturals decreased by a flat in y on grid
	private static Map<noteValType, Boolean> hasFlats = new HashMap<noteValType, Boolean>(); 
	//array of all natural notes
	private static Map<noteValType, Boolean> isNaturalNotes = new HashMap<noteValType, Boolean>(); 
	static { 
	    for (noteValType enumV : noteValType.values()) {	
	    	valMap.put(enumV.value, enumV);
	    	hasSharps.put(enumV, false);
			hasFlats.put(enumV, false);
			isNaturalNotes.put(enumV, true);
		}
	    for (noteValType enumV : bKeyVals) {
	    	isNaturalNotes.put(enumV, false);
	    }	
		isNaturalNotes.put(rest, false);
		hasFlats.put(A, true);
		hasFlats.put(B, true);
		hasFlats.put(D, true);
		hasFlats.put(E, true);
		hasFlats.put(G, true);		
		hasSharps.put(A, true);
		hasSharps.put(C, true);
		hasSharps.put(D, true);
		hasSharps.put(F, true);
		hasSharps.put(G, true);
	}
	private noteValType(int _val){value = _val;} 
	public int getVal(){return value;}
	public static noteValType getVal(int idx){return valMap.get(idx);}
	public static int getNumVals(){return valMap.size();}						//get # of values in enum
	
	public boolean chkHasSharps(){return hasSharps.get(this);}
	public boolean chkHasFlats(){return hasFlats.get(this);}
	public boolean isNaturalNote(){return isNaturalNotes.get(this);}
	public String getKeyNames(ArrayList<noteValType> keyAra){String res = "";for(int i=0;i<keyAra.size();++i){res += "|i:"+i+" : val="+keyAra.get(i); }return res;}	

}//enum nValType
