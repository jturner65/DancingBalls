package dancingBallsPKG.enums;

import java.util.HashMap;
import java.util.Map;

//MIDI file meta-event codes
public enum MidiMeta {
	SeqNumber(0x0), Text(0x1), Copyright(0x2), TrackTitle(0x3), TrackInstName(0x4), Lyric(0x5), Marker(0x6), CuePoint(0x7),
	ChPrefix(0x20), Port(0x21), EndTrack(0x2F), SetTempo(0x51), SMPTEOffset(0x54), TimeSig(0x58), KeySig(0x59), SeqSpecific(0x7F);
	
	private int value; 
	private static Map<Integer, MidiMeta> map = new HashMap<Integer, MidiMeta>(); 
	static { for (MidiMeta enumV : MidiMeta.values()) { map.put(enumV.value, enumV);}}
	private MidiMeta(int _val){value = _val;} 
	public int getVal(){return value;} 	
	public static MidiMeta getVal(int idx){return map.get(idx);}
	public static int getNumVals(){return map.size();}						//get # of values in enum			

};
