package dancingBallsPKG;

import java.util.ArrayList;
import java.util.List;

public class yuryWindow extends myDispWindow {

	//piano visualization object
	public myPianoObj dispPiano;	

	/////////////
	// ui objects 
	////////////
	//sim timestep from ui
	public float deltaT = .01f;
	//list value to use - save idx
	public int songType = 0;
	//idxs - need one per object
	public final static int
		gIDX_TimeStep 		= 0,
		gIDX_SongTypes		= 1;
	//initial values - need one per object
	public float[] uiVals = new float[]{
			deltaT,											//	timestep
			songType
	};			//values of 8 ui-controlled quantities
	public final int numGUIObjs = uiVals.length;	
	public String[] songTypeList = new String[] {"Classical","Dance","Jazz"};

	//////////////
	// local/ui interactable boolean buttons
	//////////////
	//private child-class flags - window specific
	//for every class-wide boolean make an index, and increment numPrivFlags.
	//use getPrivFlags(idx) and setPrivFlags(idx,val) to consume
	//put idx-specific code in case statement in setPrivFlags
	public static final int 
			debugAnimIDX 		= 0,					//debug
			showPianoNotes 		= 1;					//show piano
	public static final int numPrivFlags = 2;

	public yuryWindow(DancingBalls _p, String _n, int _flagIdx, int[] fc, int[] sc, float[] rd, float[] rdClosed,
			String _winTxt, boolean _canDrawTraj) {
		super(_p, _n, _flagIdx, fc, sc, rd, rdClosed, _winTxt, _canDrawTraj);
		float stY = rectDim[1]+rectDim[3]-4*yOff,stYFlags = stY + 2*yOff;
		trajFillClrCnst = DancingBalls.gui_DarkCyan;		//override this in the ctor of the instancing window class
		trajStrkClrCnst = DancingBalls.gui_Cyan;
		super.initThisWin(_canDrawTraj, true, false);
	}
	
	@Override
	protected void initMe() {
		//piano to display on size of window
		dispPiano = new myPianoObj(pa, fillClr, rectDim);		//start with 52 white keys (full keyboard)
		//called once
		initPrivFlags(numPrivFlags);
		//this window is runnable
		setFlags(isRunnable, true);
		//this window uses a customizable camera
		setFlags(useCustCam, true);
		//initial local flags
		setPrivFlags(showPianoNotes,true);
		//set offset to use for custom menu objects
		custMenuOffset = uiClkCoords[3];	

		
		//put other initialization stuff here
		
	}
	//initialize all UI buttons here
	@Override
	public void initAllPrivBtns() {
		//give true labels, false labels and specify the indexes of the booleans that should be tied to UI buttons
		truePrivFlagNames = new String[]{			//needs to be in order of privModFlgIdxs
				"Debugging","Hide Piano"
		};
		falsePrivFlagNames = new String[]{			//needs to be in order of flags
				"Enable Debug","Show Piano"
		};
		privModFlgIdxs = new int[]{					//idxs of buttons that are able to be interacted with
				debugAnimIDX,showPianoNotes
		};
		numClickBools = privModFlgIdxs.length;	
		initPrivBtnRects(0,numClickBools);
	}
	
	//add reference here to all button IDX's 
	@Override
	public void setPrivFlags(int idx, boolean val) {
		boolean curVal = getPrivFlags(idx);
		if(val == curVal) {return;}
		int flIDX = idx/32, mask = 1<<(idx%32);
		privFlags[flIDX] = (val ?  privFlags[flIDX] | mask : privFlags[flIDX] & ~mask);
		switch(idx){
			case debugAnimIDX 			: {
				break;}
			case showPianoNotes			: {
				break;}
		}
	}
	
	//initialize structure to hold modifiable menu regions
	@Override
	protected void setupGUIObjsAras(){	
		//pa.outStr2Scr("setupGUIObjsAras start");
		guiMinMaxModVals = new double [][]{
			{0,1.0f,.001f},						//timestep           		gIDX_TimeStep 	
			{0,songTypeList.length-1 ,1}
		};		//min max modify values for each modifiable UI comp	

		guiStVals = new double[]{
			uiVals[gIDX_TimeStep],
			uiVals[gIDX_SongTypes]
		};								//starting value
		
		guiObjNames = new String[]{
				"Time Step",
				"Song Type"
		};								//name/label of component	
		
		//idx 0 is treat as int, idx 1 is obj has list vals, idx 2 is object gets sent to windows
		guiBoolVals = new boolean [][]{
			{false, false, true},	//timestep           		gIDX_TimeStep 
			{true, true, true}
		};						//per-object  list of boolean flags
		
		//since horizontal row of UI comps, uiClkCoords[2] will be set in buildGUIObjs		
		guiObjs = new myGUIObj[numGUIObjs];			//list of modifiable gui objects
		if(numGUIObjs > 0){
			buildGUIObjs(guiObjNames,guiStVals,guiMinMaxModVals,guiBoolVals,new double[]{xOff,yOff});			//builds a horizontal list of UI comps
		}
//		setupGUI_XtraObjs();
	}//setupGUIObjsAras
	
	//all ui objects should have an entry here to show how they should interact
	@Override
	protected void setUIWinVals(int UIidx) {
		float val = (float)guiObjs[UIidx].getVal();
		float oldVal = uiVals[UIidx];
		//int ival = (int)val;
		if(val != uiVals[UIidx]){//if value has changed...
			uiVals[UIidx] = val;
			switch(UIidx){		
			case gIDX_TimeStep 			:{
				if(val != deltaT){deltaT = val;}
				break;}
			case gIDX_SongTypes 		:{
				songType = (int)val;
				break;}
			}
		}//if val is different
	}//setUIWinVals
	
	//handle list ui components - return display value for list-based UI object
	@Override
	protected String getUIListValStr(int UIidx, int validx) {
		switch(UIidx){
		case gIDX_SongTypes :{ return songTypeList[validx %songTypeList.length];}
		default : {break;}
	}
	return "";	}

	

	
	@Override
	protected void drawMe(float animTimeMod) {
		//all drawing stuff goes here
		

	}
	
	//draw stuff to put on screen as 2d text/images
	@Override
	protected void drawOnScreenStuff(float modAmtMillis) {
		pa.pushMatrix();pa.pushStyle();
		//move to side of menu
		pa.translate(rectDim[0],0,0);
		//draw all 2d screen data here, super-imposed over background
		if (getPrivFlags(showPianoNotes)){
			dispPiano.drawMe();
		}
		
		///
		pa.popStyle();pa.popMatrix();				
	}


	@Override
	public void drawCustMenuObjs() {
		pa.pushMatrix();				pa.pushStyle();		
		//all sub menu drawing within push mat call
		pa.translate(5,custMenuOffset+yOff);
		//draw any custom menu stuff here
		
		
		pa.popStyle();					pa.popMatrix();		
	}
	
	//any simulation stuff - executes before draw on every draw cycle
	@Override
	//modAmtMillis is time passed per frame in milliseconds
	protected void simMe(float modAmtSec) {
		// TODO Auto-generated method stub

	}

	
	//debug functions
	public void dbgFunc0(){		}	
	public void dbgFunc1(){		}	
	public void dbgFunc2(){		}	
	public void dbgFunc3(){		}	
	@Override
	public void clickDebug(int btnNum){
		pa.outStr2Scr("click debug in "+name+" : btn : " + btnNum);
		switch(btnNum){
			case 0 : {	dbgFunc0();	break;}
			case 1 : {	dbgFunc1();	break;}
			case 2 : {	dbgFunc2();	break;}
			case 3 : {	dbgFunc3();	break;}
			default : {break;}
		}		
	}//clickDebug

	@Override
	protected void setCameraIndiv(float[] camVals, float rx, float ry, float dz) {
		pa.camera(camVals[0],camVals[1],camVals[2],camVals[3],camVals[4],camVals[5],camVals[6],camVals[7],camVals[8]);      
		// puts origin of all drawn objects at screen center and moves forward/away by dz
		pa.translate(camVals[0],camVals[1],(float)dz); 
	    pa.setCamOrient();	
	}

	@Override
	protected void stopMe() {pa.outStr2Scr("Stop");}	
	@Override
	public void hndlFileLoadIndiv(String[] vals, int[] stIdx) {}
	@Override
	public List<String> hndlFileSaveIndiv() {List<String> res = new ArrayList<String>();return res;}
	@Override
	protected void processTrajIndiv(myDrawnSmplTraj drawnNoteTraj){	}
	@Override
	protected myPoint getMouseLoc3D(int mouseX, int mouseY){return pa.P(mouseX,mouseY,0);}
	@Override
	protected boolean hndlMouseMoveIndiv(int mouseX, int mouseY, myPoint mseClckInWorld){return false;}
	//alt key pressed handles trajectory
	//cntl key pressed handles unfocus of spherey
	@Override
	protected boolean hndlMouseClickIndiv(int mouseX, int mouseY, myPoint mseClckInWorld, int mseBtn) {	boolean res = checkUIButtons(mouseX, mouseY);	return res;}//hndlMouseClickIndiv
	@Override
	protected boolean hndlMouseDragIndiv(int mouseX, int mouseY, int pmouseX, int pmouseY, myPoint mouseClickIn3D, myVector mseDragInWorld, int mseBtn) {boolean res = false;return res;}	
	@Override
	protected void snapMouseLocs(int oldMouseX, int oldMouseY, int[] newMouseLoc) {}	
	@Override
	protected void hndlMouseRelIndiv() {}
	@Override
	protected void endShiftKeyI() {}
	@Override
	protected void endAltKeyI() {}
	@Override
	protected void endCntlKeyI() {}
	@Override
	protected void addSScrToWinIndiv(int newWinKey){}
	@Override
	protected void addTrajToScrIndiv(int subScrKey, String newTrajKey){}
	@Override
	protected void delSScrToWinIndiv(int idx) {}	
	@Override
	protected void delTrajToScrIndiv(int subScrKey, String newTrajKey) {}
	//resize drawn all trajectories
	@Override
	protected void resizeMe(float scale) {		dispPiano.updateGridXandY(true, rectDim);		}
	@Override
	protected void closeMe() {}
	@Override
	protected void showMe() {}

	@Override
	protected void initDrwnTrajIndiv() {}


}
