package dancingBallsPKG;

//jblas for conjugate gradient
import org.jblas.*;
import static org.jblas.DoubleMatrix.*;


//spring to attach particles
public class mySpring{//spring between a particle and a fixed location (the particle's rest position
	public myParticle a;
	public double restLen,  ks, kd;	
	public DoubleMatrix fixedPos, Jp, Jv;//jacobians for position and velocity

	public mySpring(myParticle _a, double _ks, double _kd) {
		a=_a;
		fixedPos=new DoubleMatrix( a.initPos.asDblArray());
		restLen=0;ks=_ks;kd=_kd;
		initJacobians();
	}
	
	public void initJacobians() {
		Jp = DoubleMatrix.eye(3);//overwritten when 
		Jv = DoubleMatrix.eye(3);
		Jv.muli(kd);
	}
	
	

}//class mySpring