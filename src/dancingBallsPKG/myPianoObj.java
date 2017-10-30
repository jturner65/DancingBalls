package dancingBallsPKG;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListMap;


public class myPianoObj{
	public static DancingBalls pa;
	public static DancingBallWin win;
	//dimensions of piano keys, for display and mouse-checking
	public float[][] pianoWKeyDims, pianoBKeyDims;	
	//array of note data for each piano key - played if directly clicked on
	//all notes == all notes in sequence, with lowest note == idx 0
	public NoteData[] pianoWNotes, pianoBNotes, allNotes;
	//background color of window
	public int[] winFillClr;
	//descending scale from C, to build piano roll piano
	public final nValType[] wKeyVals = new nValType[] {nValType.C, nValType.B, nValType.A, nValType.G, nValType.F,nValType.E,nValType.D},
								   bKeyVals = new nValType[] {nValType.As, nValType.Gs, nValType.Fs, nValType.Ds, nValType.Cs};

	//array of naturals drecreased by a sharp in y on grid
	public nValType[] hasSharps = new nValType[]{nValType.C, nValType.D, nValType.F,nValType.G,nValType.A};
	//array of naturals drecreased by a flat in y on grid
	public nValType[] hasFlats = new nValType[]{nValType.B, nValType.D, nValType.E,nValType.G,nValType.A};	
	//array of all natural notes
	public nValType[] isNaturalNotes = new nValType[]{nValType.A,nValType.B, nValType.C,nValType.D, nValType.E,nValType.F,nValType.G};	
	
	//sound analysis
	//harmonic series
	public float[][] pianoFreqsHarmonics, pianoMinFreqsHarmonics;
	//key cutoffs for "bass" zone, "midrange/melody" zone and "treble/cymbal" zone
//	public int bassLastKey = 36, //thr
//				midFirstKey = 31,
//				midLastKey = 64,
//				trebleFirstKey = 60;
	
	
	//number of harmonics to track
	public int numHarms = 8;
	//Array of mappings from key-1 to note name
	public String[] pianoNotes;
	//holds frequency halfway between note and next highest note
	public ConcurrentSkipListMap<Float, String> maxFreqsForNotes;
	//holds on-screen y locations of centers of each key at edge of piano
	public float[] pianoKeyCtrYLocs;
	
	
	public final float wkOff_X = .72f;	
	//location and dimension of piano keyboard in parent display window, location and size of display window
	public float[] pianoDim, winDim;		
	public float keyX, keyY;										//x, y resolution of grid/keys, mod amount for black key
	public int numWhiteKeys;										//# of white keys on piano - should be 52, maybe resize if smaller?
	public static  final int numKeys = 88;
	public int numNotesWide;										//# of notes to show as grid squares
	public myPianoObj(DancingBalls _p, DancingBallWin _win, float kx, float ky, float[] _pianoDim, int[] _winFillClr, float[] _winDim){
		pa = _p;
		win = _win;
		pianoDim = _pianoDim;
		winFillClr = new int[_winFillClr.length]; for(int i=0;i<_winFillClr.length;++i){winFillClr[i]=_winFillClr[i];}
		winDim = new float[_winDim.length];
		updateDims(kx, ky, _pianoDim, _winDim);	
	}
	//if the window or piano dimensions change, update them here
	public void updateDims(float kx, float ky, float[] _pianoDim, float[] _winDim){
		keyX = kx; keyY = ky; updatePianoDim(_pianoDim);updateWinDim(_winDim);
		numWhiteKeys = 52;//PApplet.min(52,(int)(_winDim[3]/keyY));		//height of containing window will constrain keys in future maybe.
		numNotesWide = (int)((winDim[2] - pianoDim[2])/keyX);
		buildKeyDims();
	}
	//build key dimensions array for checking and displaying
	private void buildKeyDims(){
		pianoWKeyDims = new float[numWhiteKeys][];		//88 x 5, last idx is 0 if white, 1 if black
		pianoWNotes = new NoteData[numWhiteKeys];
		pianoKeyCtrYLocs = new float[numKeys];
		int numBlackKeys = numKeys - numWhiteKeys;
		allNotes = new NoteData[numKeys];
		int allNoteIDX = numKeys-1;
		pianoBKeyDims = new float[numBlackKeys][];		//88 x 5, last idx is 0 if white, 1 if black
		pianoBNotes = new NoteData[numBlackKeys];
		float wHigh = keyY, bHigh = 2.0f * win.bkModY, wWide = win.whiteKeyWidth, bWide = .6f*win.whiteKeyWidth;	
		int blkKeyCnt = 0, octave = 8;
		float stY = pianoDim[1];
		int keyIdx = numKeys-1;
		float halfWHi = wHigh * .5f, halfBHi = bHigh * .5f;
		for(int i =0; i < numWhiteKeys; ++i){
			pianoWKeyDims[i] = new float[]{0,stY,wWide,wHigh};	
			int iMod = i % 7;
			pianoWNotes[i] = new NoteData(pa,wKeyVals[iMod], octave, pianoWKeyDims[i]);
			if(wKeyVals[iMod] == nValType.C){	octave--;}
			allNotes[allNoteIDX--] = pianoWNotes[i];
			pianoKeyCtrYLocs[keyIdx--] = pianoWKeyDims[i][1] +  halfWHi;
			if((iMod != 4) && (iMod != 0) && (i != numWhiteKeys-1)&& (i != 0)){//determine which keys get black keys
				pianoBKeyDims[blkKeyCnt] = new float[]{0,stY+(keyY-win.bkModY),bWide,bHigh};
				pianoBNotes[blkKeyCnt] = new NoteData(pa,bKeyVals[blkKeyCnt%5], octave, pianoBKeyDims[blkKeyCnt]);
				allNotes[allNoteIDX--] = pianoBNotes[blkKeyCnt];
				pianoKeyCtrYLocs[keyIdx--] = pianoBKeyDims[blkKeyCnt][1] +  halfBHi;
				blkKeyCnt++;
			}
			stY +=keyY;
		}	
	}//buildKeyDims
	
	//actual tuned piano frequencies - use these as 
	public float[] buildPianoFreqs() {
		float[] res = new float[88];
		//assume c4 is appropriately tempered
		float A4 = 440.0f,sOv2 = .025f;//5 cents per octave
		//get C4 from A4
		float C4 = (float) (A4 * Math.pow(2, (-9.0f - (sOv2 * (81.0f/144.0f)))/12.0f));
		for(int i=0;i<res.length;++i) {
			float n = i-39.0f, nOv12Sq = (n*n)/144.0f, adjTerm = (n<0 ? -(sOv2 * nOv12Sq):(sOv2 * nOv12Sq)) ;
			res[i] = (float) (C4 * Math.pow(2.0,(n + adjTerm)/12.0f));			
		}		
		return res;
	}//buildPianoFreqs
	
	//set up list of note fundamental frequencies and note names to analyze sati
	public ConcurrentSkipListMap<Float, Integer> initPianoFreqs() {
		pianoFreqsHarmonics = new float[88][];
		pianoMinFreqsHarmonics = new float[89][];		
		//freq 1/2 way between note and prev note
		//pianoMinFreqs = new float[89];
		pianoNotes = new String[88];
		maxFreqsForNotes = new ConcurrentSkipListMap<Float, String>();
		float[] pianoFreqsTuning = buildPianoFreqs();
		//frequencies taking into account inharmonicity
		//freq of A0, dist 1/2 between each half step
		//use below instead of 2nd line below for equal temperment tuning
		//float stFreq = 27.5f;
		float stFreq = pianoFreqsTuning[0];				
		float augHalf = (float) Math.exp((Math.log(2.0)/24.0f));
		//setting this up to put all freqs in as keys, to use to precalc all frequencies' cosines and sines
		ConcurrentSkipListMap<Float, Integer> allFreqsUsed = new ConcurrentSkipListMap<Float, Integer>();
		//idx 0 is halfway to note lower than lowest note - span of note i is pianoMinFreqs[i] to pianoMinFreqs[i+1]
		float stMinFreq = stFreq / augHalf;
		//pianoMinFreqs[0] = stMinFreq;
		float[] harmMin1= new float[numHarms];
		for(int h=0;h<numHarms;++h) {
			harmMin1[h] = stMinFreq * (h+1);
			allFreqsUsed.put(harmMin1[h], 1);
		}
		pianoMinFreqsHarmonics[0] = harmMin1;		//idx 0 is fundamental min freq
		String[] noteNames = {"A","A#","B","C","C#","D","D#","E","F","F#","G","G#"};
		for(int i=0;i<pianoFreqsHarmonics.length;++i){
			//comment out this line to have equal temperment tuning
			stFreq = pianoFreqsTuning[i];
			float[] harmSeries = new float[numHarms], harmMinSeries= new float[numHarms];
			for(int h=0;h<numHarms;++h) {
				harmSeries[h] = stFreq * (h+1);
				allFreqsUsed.put(harmSeries[h], 1);
			}
			pianoFreqsHarmonics[i] = harmSeries;//idx 0 of each idx is fundamental freq of each key
			pianoNotes[i] = noteNames[i%noteNames.length] + "_" + ((i+9)/noteNames.length); 
			//System.out.println("i:"+i+"|Note Name : "+pianoNotes[i]);
			stFreq *= augHalf;		//.5 way to next note
			//pianoMinFreqs[i+1] = stFreq;			
			for(int h=0;h<numHarms;++h) {
				harmMinSeries[h] = stFreq * (h+1);
				allFreqsUsed.put(harmMinSeries[h], 1);
			}
			pianoMinFreqsHarmonics[i+1] = harmMinSeries;
			//halway to next frequency - use as threshold
			maxFreqsForNotes.put(stFreq, pianoNotes[i]);
			stFreq *= augHalf;//now next note
			//pa.outStr2Scr("key : " + pianoNotes[i] + "|eq tmpr tuning : " +pianoFreqsHarmonics[i][0] + "| adjusted tuning : "+ pianoFreqsTuning[i] );
		}			
		return allFreqsUsed;
	}//initPianoFreqs
			
	public boolean chkHasSharps(nValType n){for(int i =0; i<hasSharps.length;++i){	if(hasSharps[i].getVal() == n.getVal()){return true;}}return false;}
	public boolean chkHasFlats(nValType n){for(int i =0; i<hasFlats.length;++i){	if(hasFlats[i].getVal() == n.getVal()){return true;}}return false;}
	public boolean isNaturalNote(nValType n){for(int i =0; i<isNaturalNotes.length;++i){	if(isNaturalNotes[i].getVal() == n.getVal()){return true;}}return false;}
	public String getKeyNames(ArrayList<nValType> keyAra){String res = "";for(int i=0;i<keyAra.size();++i){res += "|i:"+i+" : val="+keyAra.get(i); }return res;}	
	//checks whether keyIdx is a black key or a white key - A0 is idx 0
	public boolean isBlackKey(int keyIdx) {
		int k = ((keyIdx + 88 - 2) % 12);//relative position of key in octave 0..11 notes, where 1,3,6,8,10 are black
//		pa.outStr2Scr("keyIdx :"+keyIdx+" k : " + k + " retval : " +  ((k == 1) || (k == 3) || (k == 6) || (k == 8) || (k == 11)));
		return ((k == 1) || (k == 3) || (k == 6) || (k == 8) || (k == 11)); //retVal;		
	}

	/**
	 * draw piano and display results of note detection on piano keyboard
	 * @param showingAllBandsRes whether showing all fft results.  if not can display level results on screen of analysis
	 * @param lvlsPerPKey map sorted on volume levels, with value being piano key of frequency with that volume
	 */
	public void drawPianoBandRes( ConcurrentSkipListMap<Float, Integer> lvlsPerPKey) {
		if((lvlsPerPKey.size() > 0) && (lvlsPerPKey.firstKey()>0)) {
			pa.pushMatrix();pa.pushStyle();
			pa.translate(pianoWKeyDims[0][2],0,0);
			pa.scale(5.0f, 1.0f, 1.0f);
			for (Float freqLvl : lvlsPerPKey.keySet()) {
				if(freqLvl == 0) {break;}
				float dispLvl = pa.sqrt(freqLvl);
				
				int pianoKeyIdx = lvlsPerPKey.get(freqLvl);
				pa.pushMatrix();pa.pushStyle();
				pa.translate(0,pianoKeyCtrYLocs[pianoKeyIdx],0);
				pa.setColorValStroke(isBlackKey(pianoKeyIdx) ? pa.gui_DarkCyan : pa.gui_Cyan);					
				pa.line(0, 0, dispLvl,0);					
				pa.popStyle();pa.popMatrix();							
			}				
			pa.popStyle();pa.popMatrix();							
		}		
	}//showPianoNotes
	
	//call with small range array from each individual thread
	public void drawPlayedNote(ConcurrentSkipListMap<Float, Integer> lvlsPerPKey, float bandThresh, int clr, int numShown) {
		if((lvlsPerPKey.size() > 0) && (lvlsPerPKey.firstKey() > bandThresh)) {
			int idx = 0;	
			float highestLevel = lvlsPerPKey.firstKey();
			for(Float freqLvl : lvlsPerPKey.keySet()) {	
				if(freqLvl == 0) {break;}
				drawNoteCircle(lvlsPerPKey.get(freqLvl), idx++, freqLvl, highestLevel, clr);
				if (idx > numShown-1) {break;}
			}			
		}		
	}//drawPlayedNote

	public void drawMe(){
		//needs to be in 2D
		pa.pushMatrix();pa.pushStyle();
		pa.setColorValFill(DancingBalls.gui_Red);	pa.setColorValStroke(DancingBalls.gui_Black);
		pa.strokeWeight(1.0f);		
		pa.rect(pianoDim);		//piano box
		//white keys		
		for(int i =0; i<pianoWKeyDims.length;++i){
			pa.pushMatrix();pa.pushStyle();
			pa.setColorValFill(DancingBalls.gui_OffWhite);
			pa.noStroke();
			pa.rect(pianoWKeyDims[i]);
			pa.setColorValFill(DancingBalls.gui_Gray);			
			pa.text(""+pianoWNotes[i].nameOct, (wkOff_X+.05f)*win.whiteKeyWidth, pianoWKeyDims[i][1]+.85f*keyY);
			float stXVal = 0;
			if(chkHasSharps(pianoWNotes[i].name)){
				stXVal=pianoBKeyDims[0][2];//x start at beginning of black key
			}
			pa.setColorValStroke(DancingBalls.gui_Black);
			pa.strokeWeight(1.0f);
			pa.line(stXVal,pianoWKeyDims[i][1] , pianoWKeyDims[i][2], pianoWKeyDims[i][1]);
			pa.popStyle();pa.popMatrix();		
		}
		//black keys
		for(int i =0; i<pianoBKeyDims.length;++i){
			pa.pushMatrix();pa.pushStyle();
			pa.setColorValFill(DancingBalls.gui_Black);	pa.setColorValStroke(DancingBalls.gui_Black);
			pa.rect(pianoBKeyDims[i]);
			pa.popStyle();pa.popMatrix();		
		}
		//pa.outStr2Scr("NumKeysDrawn : "+ keyCnt , true);
		pa.popStyle();pa.popMatrix();		
	}
	
	//str is what order note is = 0 is strongest, 1 is next strongest, etc.
	public void drawNoteCircle(int idx, int ord, float str, float maxStr, int clr1) {
		pa.pushMatrix();pa.pushStyle();
		if(str == maxStr) {
			pa.setColorValFill(clr1, 255);
			//pa.fill(0,255,0,255);		
			pa.stroke(0,0,0,255);			
		} else {
			int mod = 255 - (20*ord);
			int alphaScaled = (int)(255 * str/maxStr);
			pa.fill(mod,mod,mod,alphaScaled);		
			pa.stroke(0,0,0,alphaScaled);
		}
		pa.strokeWeight(1.0f);
		pa.translate(allNotes[idx].dims[0],allNotes[idx].dims[1],0);
		float y=  allNotes[idx].dims[3] * .5f;
		pa.ellipse(allNotes[idx].dims[2]-y,y,2*y,2*y);
		pa.popStyle();pa.popMatrix();		
	}
	
	private void updateWinDim(float[] _winDim){	for(int i =0; i<_winDim.length; ++i){	winDim[i] = _winDim[i];}}
	private void updatePianoDim(float[] _pianoDim){	for(int i =0; i<_pianoDim.length; ++i){	pianoDim[i] = _pianoDim[i];}}	
	
}//myPianoObj class

//class to hold note data for piano display
class NoteData{
	public static DancingBalls pa;
	nValType name;
	String nameOct;
	int octave;
	float[] dims;
	public NoteData(DancingBalls _pa,nValType _nType, int _octave, float[] _dims) {
		pa = _pa;
		name = _nType;
		octave = _octave;
		nameOct = "" + name+octave;
		dims = _dims;
	}
	
	
}//NoteData class
