package dancingBallsPKG;

import java.util.concurrent.ConcurrentSkipListMap;

/**
 * class to hold collection of myDancingBalls configured like a snowman to respond to audio stimulation
 * @author john
 *
 */
public class myDancingSnowman {
	public DancingBalls pa;
	public DancingBallWin win;
	public String name;
	
	public boolean[] flags;
	public static final int 
		debugIDX 		= 0;		//debug mode
	public static final int numFlags = 1;
	
	public String[] objNames = new String[] {"Head","Torso","Butt","Left Hand","Right Hand"};
	
	public ConcurrentSkipListMap<String, myDancingBall> snowman;
	

	public myDancingSnowman(DancingBalls _p, DancingBallWin _win, String _name) {
		pa = _p; win = _win; name = _name;
		snowman = new ConcurrentSkipListMap<String, myDancingBall>();
		initFlags();
	}
	private void initFlags() {flags = new boolean[numFlags];for(int i=0;i<numFlags;++i) {flags[i]=false;}}
	public void setFlags(int idx, boolean val) {
		flags[idx] = val;
		switch (idx) {
		case debugIDX 			:{break;}
		}
	}//setFlags
	
	//
	public void stimMe() {
		
	}
	
	
	//draw each individual snowman component
	public void drawMe() {
		
	}

	
	
	
}//class myDancingSnowmanf
