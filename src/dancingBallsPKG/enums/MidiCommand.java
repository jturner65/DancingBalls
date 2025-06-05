package dancingBallsPKG.enums;

import java.util.HashMap;
import java.util.Map;

//Midi commands
public enum MidiCommand {
	//Channel voice messages
	NoteOff(0x80), NoteOn(0x90), PolyKeyTouch(0xA0), CntrlChange(0xB0),ProgChange(0xC0),ChanTouch(0xD0), PitchBend(0xE0),
	
	//Channel mode messages
	ChannelMode(0xB8),//seems to not be used
	
	//System exlcusive messages
	SysEx(0xF0),  SysExPkt(0xF7), 
	//SysRealTime(0xF8), SysStartCurrSeq(0xFA), SysContCurrSeq(0xFB), SysStop(0xFC),	//these are probably not present, and definitely not relevant
	
	//MIDI file-only messages
	FileMetaEvent(0xFF);
	
	private int value; 
	private static Map<Integer, MidiCommand> valmap = new HashMap<Integer, MidiCommand>();
	private static Map<Integer, MidiCommand> map = new HashMap<Integer, MidiCommand>();
	static { for (MidiCommand enumV : MidiCommand.values()) { valmap.put(enumV.value, enumV); map.put(enumV.ordinal(), enumV);}}
	private MidiCommand(int _val){value = _val;} 
	public int getVal(){return value;} 	
	public static MidiCommand getEnumByIndex(int idx){return map.get(idx);}
	public static MidiCommand getEnumFromValue(int value){return valmap.get(value);}
	public static int getNumVals(){return valmap.size();}						//get # of values in enum			
};