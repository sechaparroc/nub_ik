package nub.ik.solver.trik.heuristic;

import javafx.util.Pair;
import nub.core.constraint.BallAndSocket;
import nub.core.constraint.ConeConstraint;
import nub.core.constraint.Constraint;
import nub.core.constraint.Hinge;
import nub.ik.solver.geometric.oldtrik.NodeInformation;
import nub.ik.solver.trik.Context;
import nub.ik.solver.trik.NodeState;
import nub.primitives.Quaternion;
import nub.primitives.Vector;
import nub.processing.Scene;
import processing.core.PGraphics;
import java.util.HashMap;

public class FinalHeuristic extends Heuristic {
    /**
     * The idea of this heuristics is to apply and interchange popular CCD Step along with Triangulation step. Here most of the work is done by the first joints,
     * hence the obtained solution could not seem natural when working with unconstrained chains. For this purposes a smoothing stage is required in which each
     * joint will try to do and delegate work.
     */

    HashMap<String, Pair<Vector, Vector>> vectors = new HashMap<>();
    protected boolean _topToBottom;
    protected float _proximityWeight = 1f;
    protected float _lengthWeight = 1f;
    protected float _delegationFactor = 5f; //current joint will work at least "delegation factor" times the remaining ones
    protected int _smoothingIterations = 5;
    protected boolean _enableDelegation = false;

    public FinalHeuristic(Context context) {
        super(context);
    }

    public void setTopToBottom(boolean topToBottom){
        _topToBottom = topToBottom;
    }

    public void enableDelegation(boolean enableDelegation){
        _enableDelegation = enableDelegation;
    }


    @Override
    public void prepare() {
        //Update cache of usable chain
        _smoothAngle = (float)Math.toRadians(40);
        NodeInformation._updateCache(_context.usableChainInformation());
    }

    protected boolean _log = true;
    protected int _times = 2;

    protected float _delegationParameter = 0;
    protected float _iterationParameter = 1;


    @Override
    public void applyActions(int i) {
        if(_log) vectors.clear();
        NodeInformation j_i = _context.usableChainInformation().get(i);
        Vector eff_wrt_j_i = j_i.locationWithCache(_context.endEffectorInformation().positionCache());
        Vector target_wrt_j_i = j_i.locationWithCache(_context.worldTarget().position());

        if(_log) {
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("On i : " + i);
        }

        //If smooth is enable calculate smooth parameters
        _iterationParameter = 1f/Math.max(_smoothingIterations - _context._currentIteration(), 1);
        if(_smooth || _enableDelegation){
            //Apply delegation
            int n = _topToBottom ? _context.chain().size() - 1 - i : i + 1;
            _delegationParameter = _delegationFactor / (_delegationFactor + n - 1);
            if(_log){
                System.out.println("Delegation parameter : " + _delegationParameter);
                System.out.println("Iteration parameter : " + _iterationParameter);
            }
        }

        if(i == _context.last() - 1){
            Quaternion q_i = applyCCD(j_i, eff_wrt_j_i, target_wrt_j_i, true);
            if(_smooth || _enableDelegation) {
                q_i = new Quaternion(q_i.axis(), q_i.angle() * _iterationParameter);
                if(_smooth) q_i = _clampRotation(q_i, _smoothAngle); //clamp
            }
            j_i.rotateAndUpdateCache(q_i, false, _context.endEffectorInformation()); //Apply local rotation //TODO: Enable constraint?
            return;
        }

        NodeInformation j_i1 = _context.usableChainInformation().get(i + 1);
        j_i1.updateCacheUsingReference();
        Vector eff_wrt_j_i1 = j_i1.locationWithCache(_context.endEffectorInformation().positionCache());
        Vector target_wrt_j_i1 = j_i1.locationWithCache(_context.worldTarget().position());
        //Find the two solutions of the triangulation problem on joint j_i1
        Solution[] solutions = applyTriangulation(i, j_i, j_i1, eff_wrt_j_i1, target_wrt_j_i1, true);

        //Keep original State of J_i and J_i1
        NodeInformation endEffector =_context.endEffectorInformation();
        NodeState initial_j_i = new NodeState(j_i);
        NodeState initial_j_i1 = new NodeState(j_i1);
        NodeState initial_eff = new NodeState(endEffector);

        NodeState[] final_j_i = new NodeState[solutions.length];
        NodeState[] final_j_i1 = new NodeState[solutions.length];
        NodeState[] final_eff = new NodeState[solutions.length];

        int best = 0; // keep track of best solution
        float best_dist = Float.MAX_VALUE;
        for(int s = 0; s < solutions.length; s++){
            float a  = 0; //amount or rotation applied
            j_i1.updateCacheUsingReference();
            //Apply solution find by triangulation
            j_i1.rotateAndUpdateCache(solutions[s].quaternion(), false, endEffector);

            if(!(j_i.node().constraint() instanceof Hinge)) {
                Quaternion tw = applyCCDTwist(j_i, j_i1, j_i.locationWithCache(endEffector.positionCache()), j_i.locationWithCache(_context.worldTarget().position()), (float) Math.PI);
                j_i.rotateAndUpdateCache(tw, false, endEffector);
            }


            //Apply CCD t times (best local action if joint rotation constraints are quite different)
            j_i.rotateAndUpdateCache(applyCCD(j_i,j_i.locationWithCache(endEffector.positionCache()), j_i.locationWithCache(_context.worldTarget().position()), true), true, endEffector);
            //Apply twisting if possible


            for(int t = 0; t < _times; t++){
                j_i1.updateCacheUsingReference();
                Quaternion q_i1 = applyCCD(j_i1,j_i1.locationWithCache(endEffector.positionCache()), j_i1.locationWithCache(_context.worldTarget().position()), true);
                j_i1.rotateAndUpdateCache(q_i1, false, endEffector);
                Quaternion q_i = applyCCD(j_i,j_i.locationWithCache(endEffector.positionCache()), j_i.locationWithCache(_context.worldTarget().position()), true);
                j_i.rotateAndUpdateCache(q_i, false, endEffector);
            }
            j_i1.updateCacheUsingReference();
            //store state in final vector
            final_j_i[s] = new NodeState(j_i);
            final_j_i1[s] = new NodeState(j_i1);
            final_eff[s] = new NodeState(endEffector);

            a += _quaternionDistance(initial_j_i.rotation(), final_j_i[s].rotation());

            if(_log) {
                System.out.println("---> a : " + a);
                System.out.println("---> sol : " + (s + 1) + "work by angle 1 " + _quaternionDistance(initial_j_i.rotation(), final_j_i[s].rotation()));
                System.out.println("---> sol : " + (s + 1) + "work by angle 2 " + _quaternionDistance(initial_j_i1.rotation(), final_j_i1[s].rotation()));
            }


            float dist = Vector.distance(_context.worldTarget().position(), final_eff[s].position());
            dist = Math.min(dist / (_context.avgLength() * _context.chain().size()), 1); //distance normalized

            float desired_length = Vector.distance(_context.worldTarget().position(), _context.usableChain().get(0).position());
            float current_length = Vector.distance(final_eff[s].position(), _context.usableChain().get(0).position());
            float length_distance = Math.abs(desired_length - current_length);
            length_distance = (float) Math.min(Math.pow(length_distance / (_context.avgLength() * _context.chain().size()), 2), 1); //distance normalized

            if(_log) {
                System.out.println("---> length distance : " + length_distance);
                System.out.println("---> dist : " + dist);
                System.out.println("---> constraint error : " + solutions[s].value());
            }


            dist = _iterationParameter * dist + solutions[s].value() + a * 0.5f;// + length_distance * _lengthWeight;


            //if(_iterationParameter < 1) dist = solutions.get(s).getValue();
            if(dist < best_dist){
                best_dist = dist;
                best = s;
            }
            //reset state
            j_i.setCache(initial_j_i.position().get(), initial_j_i.orientation().get());
            j_i.node().setRotation(initial_j_i.rotation().get());
            j_i1.setCache(initial_j_i1.position().get(), initial_j_i1.orientation().get());
            j_i1.node().setRotation(initial_j_i1.rotation().get());
            endEffector.setCache(initial_eff.position().get(), initial_eff.orientation().get());

            if(_log) {
                vectors.put("sol " + (s + 1), new Pair<>(final_j_i[s].position().get(), final_j_i1[s].position().get()));
                vectors.put("sol " + (s + 1) + " d : " + String.format("%.3f", dist), new Pair<>(final_j_i1[s].position().get(), final_eff[s].position().get()));
            }
        }

        if(_log){
            System.out.println("best i :" + final_j_i[best].rotation().axis() + " a : " + final_j_i[best].rotation().angle());
            System.out.println("best i + 1 :" + final_j_i1[best].rotation().axis() + " a : " + final_j_i1[best].rotation().angle());
            System.out.println("best i :" + j_i.node().rotation().axis() + " a : " + j_i.node().rotation().angle());
            System.out.println("best i + 1 :" + j_i1.node().rotation().axis() + " a : " + j_i1.node().rotation().angle());

        }

        //Apply best solution and smooth if required
        if(_smooth){
            applySmoothing(i, final_j_i[best].rotation(), final_j_i1[best].rotation());
        }else {
            j_i.setCache(final_j_i[best].position().get(), final_j_i[best].orientation().get());
            Constraint c_i = j_i.node().constraint();
            j_i.node().setConstraint(null);
            j_i.node().setRotation(final_j_i[best].rotation().get());
            j_i.node().setConstraint(c_i);

            j_i1.setCache(final_j_i1[best].position().get(), final_j_i1[best].orientation().get());
            Constraint c_i1 = j_i1.node().constraint();
            j_i1.node().setConstraint(null);
            j_i1.node().setRotation(final_j_i1[best].rotation().get());
            j_i1.node().setConstraint(c_i1);
            endEffector.setCache(final_eff[best].position().get(), final_eff[best].orientation().get());
        }
    }

    protected float smoothDistance(Vector v, Vector u){
        float dist = Vector.distance(u, v);
        dist = Math.min(dist / (_context.avgLength() * _context.chain().size()), 0.99f); //normalize distance
        return 1 - dist;
    }

    protected void applySmoothing(int i, Quaternion final_j_i, Quaternion final_j_i1){
        //Apply best solution and smooth if required
        if(_smooth){
            NodeInformation j_i = _context.usableChainInformation().get(i);
            NodeInformation j_i1 = _context.usableChainInformation().get(i + 1);
            NodeInformation endEffector = _context.endEffectorInformation();

            //Apply smoothing based on distance to target
            float dist = Vector.distance(endEffector.positionCache(), _context.worldTarget().position());
            dist = Math.min(dist / (_context.avgLength() * _context.chain().size()), 0.8f);
            float w = 1 - dist;
            w = 1;
            //Apply delegation
            int n = _topToBottom ? _context.chain().size() - 1 - i : i + 1;
            float k = _delegationFactor / (_delegationFactor + n - 1);
            //k = k/Math.max(1, _smoothingIterations - _context._currentIteration());
            //k += (_context._currentIteration())*(1.f - k)/_smoothingIterations;
            k = Math.min(1,k);
            w = w * k;

            if(_log) System.out.println("i : " +  i + " n : " + n + " w " + w + " delegation factor " + k);

            Quaternion q_i = Quaternion.compose(j_i.node().rotation().inverse(), final_j_i);
            q_i = new Quaternion(q_i.axis(), q_i.angle() * w);
            Quaternion q_i1 = Quaternion.compose(j_i1.node().rotation().inverse(), final_j_i1);
            q_i1 = new Quaternion(q_i1.axis(), q_i1.angle() * w);
            //clamp
            q_i = _clampRotation(q_i, _smoothAngle);
            q_i1 = _clampRotation(q_i1, _smoothAngle);

            j_i.rotateAndUpdateCache(q_i, true, endEffector);
            j_i1.updateCacheUsingReference();
            j_i1.rotateAndUpdateCache(q_i1, true, endEffector);
        }
    }



    protected class Solution{
        protected Quaternion _quaternion;
        protected float _value;

        protected Solution(Quaternion q, float v){
            _quaternion = q;
            _value = v;
        }

        protected void setQuaternion(Quaternion q){
            _quaternion = q;
        }

        protected void setValue(float v){
            _value = v;
        }

        protected Quaternion quaternion(){
            return _quaternion;
        }

        protected float value(){
            return _value;
        }
    }

    protected Solution[] applyTriangulation(int i , NodeInformation j_i, NodeInformation j_i1, Vector endEffector, Vector target, boolean checkHinge){
        //checkHinge = false;
        Vector v_i = j_i1.locationWithCache(j_i.positionCache());
        Vector normal;
        //In this case we apply triangulation over j_i1
        if(checkHinge && j_i1.node().constraint() instanceof Hinge){
            //Project endEffector to lie on the plane defined by the axis of rotation
            Hinge h_i1 = ((Hinge) j_i1.node().constraint());
            //1. find rotation axis
            normal = h_i1.orientation().rotate(new Vector(0,0,1));
            if(_log) System.out.println("normal " + normal);
            normal = j_i1.node().rotation().inverse().rotate(normal);
            normal.normalize();
            //2. project target and end effector
            v_i = Vector.projectVectorOnPlane(v_i, normal);
            endEffector = Vector.projectVectorOnPlane(endEffector, normal);
            target = Vector.projectVectorOnPlane(target, normal);
        } else if(checkHinge && j_i1.node().constraint() instanceof BallAndSocket){
            Hinge h_i1 = fixConeConstraintAlt(j_i1, endEffector, target);
            //1. find rotation axis
            normal = h_i1.orientation().rotate(new Vector(0,0,1));
            if(_log) System.out.println("normal " + normal);
            normal = j_i1.node().rotation().inverse().rotate(normal);
            normal.normalize();
            //2. project target and end effector
            v_i = Vector.projectVectorOnPlane(v_i, normal);

            if(_log){
                System.out.println("eff without proj " + endEffector);
                System.out.println("target without proj " + target);
            }
            endEffector = Vector.projectVectorOnPlane(endEffector, normal);
            target = Vector.projectVectorOnPlane(target, normal);

            if(_log){
                System.out.println("eff with proj " + endEffector);
                System.out.println("target with proj " + target);
            }

        } else{
            normal = Vector.cross(endEffector, target, null);
            if(normal.squaredNorm() < 0.0001f){
                normal = Vector.cross(target, v_i, null);
            }
            //pick any vector if all are collinear
            if(normal.squaredNorm() < 0.0001f){
                normal = target.orthogonalVector();
            }
            normal.normalize();
        }
        //Find the two solutions of the triangulation problem assuming no constraints
        Vector a = v_i;
        Vector a_neg = Vector.multiply(a, -1);
        Vector b = endEffector;
        Vector c = Vector.subtract(target, a);
        float a_mag = a.magnitude(), b_mag = b.magnitude(), c_mag = c.magnitude();

        if(_log) {
            System.out.println("Vec a : " + a + "mag" + a.magnitude());
            System.out.println("Vec b : " + b + "mag" + b.magnitude());
            System.out.println("Vec c : " + c + "mag" + c.magnitude());
        }

        //If smoothing is required we delegate a uniform amount of work to the remaining joints considering that the target is closer
        if(_smooth || _enableDelegation){
            //Apply delegation
            float k = Math.min(1,_delegationParameter);
            k += (1.f - k) * Math.min(1, _context._currentIteration() * 1f / _smoothingIterations);
            float remaining_work = 1 - k;

            Vector error = Vector.subtract(Vector.subtract(b,a), c);
            c.add(Vector.multiply(error,remaining_work));
            c_mag = c.magnitude();
            if(_log) {
                System.out.println("delegation factor : " + k);
                System.out.println("Error : " + error);
                System.out.println("Error covered by current joint: " + Vector.multiply(error,remaining_work));
                System.out.println("smooth Vec c : " + c + "mag" + c.magnitude());
            }
        }


        float angle = Math.min(Math.max(Vector.dot(a, b) / (a_mag * b_mag), -1), 1);
        angle = (float) (Math.acos(angle));
        if(_log) {
            System.out.println("dot a ,b  : " + Vector.dot(a, b));
            System.out.println("a mag * b mag : " + (a_mag * b_mag));
            System.out.println("result : " + Vector.dot(a, b) / (a_mag * b_mag));
            System.out.println("angle : " + Math.toDegrees(angle));
            System.out.println("cross : " + Vector.cross(b, a_neg, null));
            System.out.println("Normal : " + normal);
            System.out.println("Dot : " + Vector.dot(Vector.cross(b, a_neg, null), normal));

        }

        float angle_1, angle_2;

        if(Vector.dot(Vector.cross(b, a_neg, null), normal) < 0){
            angle = -angle;
        }

        //Find limits centered at angle
        float max = (float)Math.PI;
        float min = -max;
        if(checkHinge && (j_i1.node().constraint() instanceof BallAndSocket || j_i1.node().constraint() instanceof Hinge)) {
            Quaternion q_i1 = j_i1.node().rotation().get(); //w.r.t hinge reference
            Hinge h_i1 = null;

            if(j_i1.node().constraint() instanceof Hinge) h_i1 = ((Hinge) j_i1.node().constraint());
            else if(j_i1.node().constraint() instanceof BallAndSocket) h_i1 = fixConeConstraintAlt(j_i1, endEffector, target);

            q_i1 = Quaternion.compose(h_i1.orientation().inverse(), q_i1);
            q_i1 = Quaternion.compose(q_i1, h_i1.restRotation());

            Vector rotationAxis = new Vector(q_i1._quaternion[0], q_i1._quaternion[1], q_i1._quaternion[2]);
            rotationAxis = Vector.projectVectorOnAxis(rotationAxis, new Vector(0, 0, 1));
            //Get rotation component on Axis direction w.r.t reference
            Quaternion rotationTwist = new Quaternion(rotationAxis.x(), rotationAxis.y(), rotationAxis.z(), q_i1.w());
            float deltaAngle = rotationTwist.angle();
            if (rotationAxis.dot(new Vector(0, 0, 1)) < 0) deltaAngle *= -1;

            float current = deltaAngle;
            //Center limits
            max = h_i1.maxAngle() - current;
            min = -h_i1.minAngle() - current;
            if(_log) {
                System.out.println("--- max hinge : " + Math.toDegrees(h_i1.maxAngle()));
                System.out.println("--- min hinge : " + Math.toDegrees(h_i1.minAngle()));
                System.out.println("--- current : " + Math.toDegrees(deltaAngle));
                System.out.println("--- rotation axis : " + rotationAxis);
                System.out.println("--- rotation tw : " + rotationTwist.axis() + " a : " + rotationTwist.angle());
            }
        }
        if(_log) {
            System.out.println("--- max limit : " + Math.toDegrees(max));
            System.out.println("--- min limit : " + Math.toDegrees(min));
        }

        if (a_mag + b_mag <= c_mag) {
            if(_log) System.out.println("---case1 : extend chain!!! ");
            //Chain must be extended as much as possible
            if(_log) {
                System.out.println("current : " + (Math.toDegrees(angle)));
                System.out.println("expected : " + (Math.toDegrees(Math.PI)));
            }

            if(angle == 0){
                angle_1 = (float) (Math.PI);
                angle_2 = -(float) (Math.PI);
            }else {
                angle_1 = Math.signum(angle) * (float) (Math.PI) - angle;
                angle_2 = (float)(-Math.signum(angle_1) * 2*Math.PI + angle_1);
            }
        } else if (c_mag < Math.abs(a_mag - b_mag)) {
            if(_log) System.out.println("---case2 : contract chain!!! on node : ");
            //Chain must be contracted as much as possible
            if(_log) {
                System.out.println("current : " + (Math.toDegrees(angle)));
                System.out.println("expected : " + 0);
            }

            angle_1 = -angle;
            angle_2 = (float)(-Math.signum(angle_1) * 2*Math.PI + angle_1);
        } else {
            //Apply law of cosines
            float current = angle;

            float expected = Math.min(Math.max(-(c_mag * c_mag - a_mag * a_mag - b_mag * b_mag) / (2f * a_mag * b_mag), -1), 1);
            expected = (float) (Math.acos(expected));

            if(_log) {
                System.out.println("current : " + Math.toDegrees(current));
                System.out.println("expected : " + Math.toDegrees(expected));
            }

            float sign = current != 0 ? Math.signum(current) : 1;
            angle_1 = sign * expected - current;
            if(Math.abs(current) < Math.abs(expected)){
                angle_2 = current - Math.signum(current) * expected;
            } else{
                angle_2 = sign * (float)(2 * Math.PI - expected) - current;
            }

            if(_log) {
                System.out.println("--> angle 1 : " + Math.toDegrees(angle_1));
                System.out.println("--> angle 2: " + Math.toDegrees(angle_2));
            }
        }
        if(_log) {
            System.out.println("--> angle 1 : " + Math.toDegrees(angle_1));
            System.out.println("--> angle 2 : " + Math.toDegrees(angle_2));
        }

        //Constraint the angles according to the joint limits (prefer the solution with least damping)
        float constrained_angle_1 = Math.min(max, Math.max(min,angle_1));
        float constrained_angle_2 = Math.min(max, Math.max(min, angle_2));;

        if(_log) {
            System.out.println("--> constrained angle 1 : " + Math.toDegrees(constrained_angle_1));
            System.out.println("--> constrained angle 2 : " + Math.toDegrees(constrained_angle_2));
            System.out.println("--> ratio angle 1 : " + (Math.abs(constrained_angle_1) + 1)/(Math.abs(angle_1) + 1));
            System.out.println("--> ration angle 2 : " + (Math.abs(constrained_angle_2) + 1)/(Math.abs(angle_2) + 1));

        }

        Solution[] deltas = new Solution[2];
        deltas[0] = new Solution(new Quaternion(normal, constrained_angle_1), 1 - (Math.abs(constrained_angle_1) + 1)/(Math.abs(angle_1) + 1));
        deltas[1] = new Solution(new Quaternion(normal, constrained_angle_2), 1 - (Math.abs(constrained_angle_2) + 1)/(Math.abs(angle_2) + 1));
        for(Solution delta : deltas){
            if(_smooth) delta.setQuaternion(_clampRotation(delta.quaternion(), _smoothAngle));
            if(j_i1.node().constraint() != null){
                delta.setQuaternion(j_i1.node().constraint().constrainRotation(delta.quaternion(), j_i1.node()));
            }
            if(_log) {
                System.out.println("--> delta : " + delta.quaternion().axis() + " angle : "+ Math.toDegrees(delta.quaternion().angle()));
            }
        }
        return deltas;
    }

    //Try to approach to target final position by means of twisting
    protected Quaternion applyCCDTwist(NodeInformation j_i, NodeInformation j_i1, Vector endEffector, Vector target, float maxAngle){
        Vector j_i_to_eff_proj, j_i_to_target_proj;
        Vector tw = j_i1.node().translation(); // w.r.t j_i
        //Project the given vectors in the plane given by twist axis
        try {
            j_i_to_target_proj = Vector.projectVectorOnPlane(target, tw);
            j_i_to_eff_proj = Vector.projectVectorOnPlane(endEffector, tw);
        } catch(Exception e){
            return new Quaternion(tw, 0);
        }

        //Perform this operation only when Projected Vectors have not a despicable length
        if (j_i_to_target_proj.magnitude() < 0.3 * target.magnitude() && j_i_to_eff_proj.magnitude() < 0.3 * endEffector.magnitude()) {
            return new Quaternion(tw, 0);
        }

        //Find the angle between projected vectors
        float angle = Vector.angleBetween(j_i_to_eff_proj, j_i_to_target_proj);
        //clamp angle
        angle = Math.min(angle, maxAngle);
        if(Vector.cross(j_i_to_eff_proj, j_i_to_target_proj, null).dot(tw) <0)
            angle *= -1;

        Quaternion twist = new Quaternion(tw, angle);
        if(_smooth){
            twist = _clampRotation(twist, _smoothAngle);
        }
        return twist;
    }


    protected Quaternion applyCCD(NodeInformation j_i, Vector endEffector, Vector target, boolean checkHinge){
        Vector p = endEffector;
        Vector q = target;
        if(checkHinge && j_i.node().constraint() != null && j_i.node().constraint() instanceof Hinge) {
            Hinge h = (Hinge) j_i.node().constraint();
            Quaternion quat = Quaternion.compose(j_i.node().rotation().inverse(), h.idleRotation());
            Vector tw = h.restRotation().rotate(new Vector(0, 0, 1));
            tw = quat.rotate(tw);
            //Project b & c on the plane of rot
            p = Vector.projectVectorOnPlane(p, tw);
            q = Vector.projectVectorOnPlane(q, tw);
        }
        //Apply desired rotation removing twist component
        Quaternion delta = new Quaternion(p, q);
        if(_smooth){
            delta = _clampRotation(delta, _smoothAngle);
        }
        if(j_i.node().constraint() != null){
            delta = j_i.node().constraint().constrainRotation(delta, j_i.node());
        }
        return delta;
    }


    /*Check the axis in which there is greater change
    *
    * Returns: a normal pointing out the found plane along with the max and min angle of rotation
    * */
    public Hinge fixConeConstraint(NodeInformation j_i1, Vector endEffector, Vector target){
        BallAndSocket cone = (BallAndSocket) j_i1.node().constraint();
        Quaternion node_rotation = j_i1.node().rotation();
        Quaternion idle = cone.idleRotation();
        Quaternion rest = cone.restRotation();
        Quaternion offset = cone.offset();

        //Find desired rotation
        Quaternion rotation = new Quaternion(endEffector, target);
        Quaternion change = Quaternion.compose(idle.inverse(), Quaternion.compose(Quaternion.compose(node_rotation, rotation), offset)); //w.r.t idle

        //Find expected bone position w.r.t idle
        Vector new_pos = change.rotate(cone.restRotation().rotate(new Vector(0, 0, 1))); //get twist desired target position

        //Find the chord of the ellipse defining the revolutionary joint
        Vector uvec = Quaternion.multiply(cone.restRotation(), new Vector(0, 1, 0));
        Vector rvec = Quaternion.multiply(cone.restRotation(), new Vector(1, 0, 0));
        Vector line = Quaternion.multiply(cone.restRotation(), new Vector(0, 0, 1));

        float PI = (float) Math.PI;
        Vector f = new_pos.get();
        float scalar = Vector.dot(new_pos, line) / line.magnitude();
        Vector proj = Vector.multiply(line, scalar);
        Vector adjust = Vector.subtract(new_pos, proj);
        float xaspect = Vector.dot(adjust, rvec);
        float yaspect = Vector.dot(adjust, uvec);

        float clampDown = cone.down();
        float clampUp = cone.up();
        float clampLeft = cone.left();
        float clampRight = cone.right();

        float xbound, x_negbound, ybound, y_negbound;
        if(xaspect >= 0){
            xbound = clampRight;
            x_negbound = clampLeft;
        } else{
            xbound = clampLeft;
            x_negbound = clampRight;
        }

        if(yaspect >= 0){
            ybound = clampUp;
            y_negbound = clampDown;
        } else{
            ybound = clampDown;
            y_negbound = clampUp;
        }
        if (scalar < 0) {
            if (xbound > PI / 2. && ybound > PI / 2.) {
                xbound = proj.magnitude() * (float) (Math.tan(PI - xbound));
                x_negbound = proj.magnitude() * (float) (Math.tan(PI - x_negbound));
                ybound = proj.magnitude() * (float) (Math.tan(PI - ybound));
                y_negbound = proj.magnitude() * (float) (Math.tan(PI - y_negbound));
            } else {
                xbound = proj.magnitude() * (float) (Math.tan(xbound));
                x_negbound = proj.magnitude() * (float) (Math.tan(PI - x_negbound));
                ybound = proj.magnitude() * (float) (Math.tan(ybound));
                y_negbound = proj.magnitude() * (float) (Math.tan(PI - y_negbound));
                proj.multiply(-1.f);
            }
        } else {
            xbound = xbound > PI / 2. ? proj.magnitude() * (float) (Math.tan(PI / 2.f))
                    : proj.magnitude() * (float) (Math.tan(xbound));
            x_negbound = x_negbound > PI / 2. ? proj.magnitude() * (float) (Math.tan(PI / 2.f))
                    : proj.magnitude() * (float) (Math.tan(x_negbound));
            ybound = ybound > PI / 2. ? proj.magnitude() * (float) (Math.tan(PI / 2.f))
                    : proj.magnitude() * (float) (Math.tan(ybound));
            y_negbound = y_negbound > PI / 2. ? proj.magnitude() * (float) (Math.tan(PI / 2.f))
                    : proj.magnitude() * (float) (Math.tan(y_negbound));
        }

        Vector v1 = cone.closestPointToEllipse(xbound, ybound, new Vector(xaspect, yaspect));

        //float a1 = (float) (Math.atan2(yaspect, xaspect));
        //float cos1 = (float) (Math.cos(a1));
        //float sin1 = (float) (Math.sin(a1));
        //float rad1 = 1.f / (float) Math.sqrt(((cos1 * cos1) / (xbound * xbound)) + ((sin1 * sin1) / (ybound * ybound)));
        //Vector v1 = new Vector(rad1 * cos1, rad1 * sin1);


        float a2 = (float) (Math.atan2(-v1.y(), -v1.x()));
        float cos2 = (float) (Math.cos(a2));
        float sin2 = (float) (Math.sin(a2));
        float rad2 = 1.f / (float) Math.sqrt(((cos2 * cos2) / (x_negbound * x_negbound)) + ((sin2 * sin2) / (y_negbound * y_negbound)));
        Vector v2 = new Vector(rad2 * cos2, rad2 * sin2);

        System.out.println("v1 " + v1 + " v2 " + v2);

        //Vector v = closestPointToEllipse(xbound, ybound, new Vector(xaspect, yaspect));
        Vector f1 = Vector.add(proj, Vector.multiply(rvec, v1.x()));
        f1 = Vector.add(f1, Vector.multiply(uvec, v1.y()));
        f1.normalize();
        f1.multiply(target.magnitude());


        Vector f2 = Vector.add(proj, Vector.multiply(rvec, v2.x()));
        f2 = Vector.add(f2, Vector.multiply(uvec, v2.y()));
        f2.normalize();
        f2.multiply(target.magnitude());

        Vector tw = rest.rotate(new Vector(0, 0, 1)); // w.r.t idle

        //f1 to j_i1 node
        Quaternion idle_to_j_i1 = Quaternion.compose(node_rotation.inverse(),idle);
        Vector f1_wrt_ji1 = idle_to_j_i1.rotate(f1);
        Vector f2_wrt_ji1 = idle_to_j_i1.rotate(f2);
        Vector tw_wrt_ji1 = idle_to_j_i1.rotate(tw);


        Quaternion q1 = new Quaternion(tw , f1);
        Quaternion q2 = new Quaternion(tw , f2);

        Quaternion rot1 = Quaternion.compose(node_rotation.inverse(), Quaternion.compose(idle, Quaternion.compose(q1, offset.inverse())));
        Quaternion rot2 = Quaternion.compose(node_rotation.inverse(), Quaternion.compose(idle, Quaternion.compose(q2, offset.inverse())));

        Quaternion rot11 = new Quaternion(tw_wrt_ji1, f1_wrt_ji1);
        Quaternion rot22 = new Quaternion(tw_wrt_ji1, f2_wrt_ji1);

        if(_log) {
            System.out.println("rot1 " + q1.axis() + Math.toDegrees(q1.angle()));
            System.out.println("rot2 " + q2.axis() + Math.toDegrees(q2.angle()));

            System.out.println("rot1 " + rot1.axis() + Math.toDegrees(rot1.angle()));
            System.out.println("rot2 " + rot2.axis() + Math.toDegrees(rot2.angle()));

            System.out.println("rot11 " + rot11.axis() + Math.toDegrees(rot11.angle()));
            System.out.println("rot22 " + rot22.axis() + Math.toDegrees(rot22.angle()));

        }

        if(_log) {
            vectors.put("f1 ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(f1_wrt_ji1)));
            vectors.put("f2 ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(f2_wrt_ji1)));
            vectors.put("tw ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(Vector.multiply(tw_wrt_ji1, 10))));
            vectors.put("diff ", new Pair<>(j_i1.node().worldLocation(endEffector), j_i1.node().worldLocation(target)));
            vectors.put("diff >_<" + Vector.distance(j_i1.positionCache(), j_i1.node().position()), new Pair<>(j_i1.positionCache(), j_i1.node().position()));
        }

        //return Hinge parameters
        Vector normal = rot11.axis();

        Hinge h = new Hinge(rot22.angle(), rot11.angle(), new Quaternion(), normal.orthogonalVector(), normal);

        return h;
    }


    public Hinge fixConeConstraintAlt(NodeInformation j_i1, Vector endEffector, Vector target) {
        BallAndSocket cone = (BallAndSocket) j_i1.node().constraint();
        Quaternion node_rotation = j_i1.node().rotation();
        Quaternion idle = cone.idleRotation();
        Quaternion rest = cone.restRotation();
        Quaternion offset = cone.offset();

        //Quaternion change = Quaternion.compose(idle.inverse(), Quaternion.compose(node_rotation, offset)); //w.r.t idle

        Vector uvec = new Vector(0, 1, 0);
        Vector rvec = new Vector(1, 0, 0);
        Vector line = new Vector(0, 0, 1);

        //1. Find normal
        Vector normal = Vector.cross(endEffector, target, null);
        normal.normalize();
        System.out.println("||---> normal : " + normal.normalize(null));

        //2. Express w.r.t idle
        Quaternion jointToIdle = Quaternion.compose(rest.inverse(), Quaternion.compose(idle.inverse(), Quaternion.compose(offset, node_rotation)));
        System.out.println("<<<<jointToIdle : " + jointToIdle.axis() + jointToIdle.angle());

        Vector normal_wrt_idle = jointToIdle.rotate(normal);
        if (normal.squaredNorm() < 0.0001) normal_wrt_idle = uvec;
        normal_wrt_idle = Vector.projectVectorOnPlane(normal_wrt_idle, line);

        //3. Find the ellipse - line intersection
        float angle;

        System.out.println("==== normal " + normal_wrt_idle);

        if(Math.abs(normal_wrt_idle.y()) < 0.00001){
            System.out.println("==== edge case " + normal_wrt_idle.y());
            //is a vertical line
            angle = (float) Math.PI / 2;
        } else{ //find the circle line intersection
            float x;
            float m = -normal_wrt_idle.x()/ normal_wrt_idle.y();
            System.out.println("==== m " + m);
            x = (float) Math.sqrt(1 / (1 + m*m));
            angle = (float) Math.atan2(m*x, x);
            System.out.println("==== angle : " + Math.toDegrees(angle));
            System.out.println("==== x : " + x + " y " + m*x);
        }

        //angle sign
        float radius_x, radius_y = (float) Math.tan(cone.up()), radius_neg_y = (float) Math.tan(cone.down()), radius_neg_x;

        if(angle < Math.PI / 2){
            radius_x = (float) Math.tan(cone.right());
            radius_neg_x = (float) Math.tan(cone.left());
        } else{
            radius_x = (float) Math.tan(cone.left());
            radius_neg_x = (float) Math.tan(cone.right());
        }

        if(angle < 0){
            radius_y = (float) Math.tan(cone.down());
            radius_neg_y = (float) Math.tan(cone.up());
        }


        //6. find theta min/ theta max
        float cos1 = (float) (Math.cos(angle));
        float sin1 = (float) (Math.sin(angle));
        float rad1 = (radius_x * radius_y) / (float) Math.sqrt( (cos1 * cos1) * (radius_y * radius_y) + (sin1 * sin1)  * (radius_x * radius_x));
        Vector v1 = new Vector(rad1 * cos1, rad1 * sin1);

        float angle2 = (float)( angle > 0 ? -Math.PI + angle : Math.PI + angle);

        float cos2 = (float) (Math.cos(angle2));
        float sin2 = (float) (Math.sin(angle2));
        float rad2 = (radius_neg_x * radius_neg_y) / (float) Math.sqrt( (cos2 * cos2) * (radius_neg_y * radius_neg_y) + (sin2 * sin2)  * (radius_neg_x * radius_neg_x));
        Vector v2 = new Vector(rad2 * cos2, rad2 * sin2);

        System.out.println("angle ---- " + Math.toDegrees(angle));
        System.out.println("v1 ---- " + v1);
        System.out.println("v2 ---- " + v2);

        Vector f1 = Vector.add(line, Vector.multiply(rvec, v1.x()));
        f1 = Vector.add(f1, Vector.multiply(uvec, v1.y()));
        f1.normalize();
        f1.multiply(target.magnitude());

        Vector f2 = Vector.add(line, Vector.multiply(rvec, v2.x()));
        f2 = Vector.add(f2, Vector.multiply(uvec, v2.y()));
        f2.normalize();
        f2.multiply(target.magnitude());

        Vector tw = new Vector(0, 0, 1); // w.r.t idle

        //f1 to j_i1 node
        Quaternion idle_to_j_i1 = (Quaternion.compose(node_rotation.inverse(),Quaternion.compose(offset.inverse(), Quaternion.compose(idle, rest))));
        Vector f1_wrt_ji1 = idle_to_j_i1.rotate(f1);
        Vector f2_wrt_ji1 = idle_to_j_i1.rotate(f2);
        Vector tw_wrt_ji1 = idle_to_j_i1.rotate(tw);
        Vector rg_wrt_ji1 = idle_to_j_i1.rotate(rvec);
        Vector up_wrt_ji1 = idle_to_j_i1.rotate(uvec);



        Quaternion q1 = new Quaternion(tw , f1);
        Quaternion q2 = new Quaternion(tw , f2);

        Quaternion rot11 = new Quaternion(tw_wrt_ji1, f1_wrt_ji1);
        Quaternion rot22 = new Quaternion(tw_wrt_ji1, f2_wrt_ji1);

        if(_log) {
            System.out.println("rot1 " + q1.axis() + Math.toDegrees(q1.angle()));
            System.out.println("rot2 " + q2.axis() + Math.toDegrees(q2.angle()));

            System.out.println("rot11 " + rot11.axis() + Math.toDegrees(rot11.angle()));
            System.out.println("rot22 " + rot22.axis() + Math.toDegrees(rot22.angle()));

        }
        //return Hinge parameters
        Vector normal1 = rot11.axis();
        System.out.println("||---- normal 1 " + normal1);

        Hinge h = new Hinge(rot22.angle(), rot11.angle(), cone.idleRotation(), normal1.orthogonalVector(), normal1);

        if(_log) {
            vectors.put("f1 ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(f1_wrt_ji1)));
            vectors.put("f2 ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(f2_wrt_ji1)));
            vectors.put("tw ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(Vector.multiply(tw_wrt_ji1, 10))));
            vectors.put("right ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(Vector.multiply(rg_wrt_ji1, 30))));
            vectors.put("up ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(Vector.multiply(up_wrt_ji1, 30))));

            vectors.put("normal! ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(Vector.multiply(normal1, 30))));
            vectors.put("real_normal! ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(Vector.multiply(normal, 30))));
            vectors.put("diff ", new Pair<>(j_i1.node().worldLocation(endEffector), j_i1.node().worldLocation(target)));
            vectors.put("eff ", new Pair<>(j_i1.node().position(), j_i1.node().worldLocation(endEffector)));
        }


        return h;
    }

        @Override
    public NodeInformation[] nodesToModify(int i) {
        //return new NodeInformation[]{_context.usableChainInformation().get(i - 1), _context.usableChainInformation().get(i)};
        return null;
    }

    protected static Quaternion _clampRotation(Quaternion rotation, float maxAngle) {
        float angle = rotation.angle();
        if (Math.abs(angle) > maxAngle) {
            rotation = new Quaternion(rotation.axis(), (Math.signum(angle) * maxAngle));
        }
        return rotation;
    }



    public void drawVectors(Scene scene){
        if(!_log) return;
        PGraphics pg = scene.context();
        int i = 0;
        for(String key : vectors.keySet()){
            Pair<Vector, Vector> pair = vectors.get(key);
            if(key.equals("normal")){
                Vector n = Vector.subtract(pair.getValue(), pair.getKey());
                Vector a1 =  Vector.orthogonalVector(n);
                a1.normalize();
                a1.multiply(n.magnitude() * 9);
                Vector a2 =  Vector.cross(n, a1, null);
                a2.normalize();
                a2.multiply(n.magnitude() * 8);
                pg.fill(255,255,255, 50);
                pg.noStroke();
                pg.beginShape();
                    pg.vertex(a1.x() + a2.x() + pair.getKey().x(), a1.y() + a2.y() + pair.getKey().y(), a1.z() + a2.z() + pair.getKey().z());
                    pg.vertex(a1.x() - a2.x() + pair.getKey().x(), a1.y() - a2.y() + pair.getKey().y(), a1.z() - a2.z() + pair.getKey().z());
                    pg.vertex(-a1.x() -a2.x() + pair.getKey().x(), -a1.y() - a2.y() + pair.getKey().y(), -a1.z() - a2.z() + pair.getKey().z());
                    pg.vertex(-a1.x() + a2.x() + pair.getKey().x(), -a1.y() + a2.y() + pair.getKey().y(), -a1.z() + a2.z() + pair.getKey().z());
                pg.endShape();
            }


            Vector t = scene.screenLocation(pair.getValue());
            pg.noStroke();
            pg.fill(scene.pApplet().noise(1000 + 10*i) * 255, scene.pApplet().noise(80 + 10*i) * 255, scene.pApplet().noise(235 + 10*i) * 255);
            scene.drawArrow(pair.getKey(), pair.getValue(), 1.5f);
            pg.noLights();
            scene.beginHUD();
            pg.fill(255,255,255);
            pg.text(key, t.x(), t.y());
            scene.endHUD();
            pg.lights();
            i++;

        }
    }

    protected float _quaternionDistance(Quaternion q1, Quaternion q2){
        float dot = (float) Math.pow(Quaternion.dot(q1, q2), 2);
        return (1 - dot);
    }
}

    /*OLD PROCEDURE
    public void applyActions(int i) {
        vectors.clear();
        NodeInformation j_i = _context.usableChainInformation().get(i);
        if(i == _context.last() - 1){
            Vector endEffector = j_i.locationWithCache(_context.endEffectorInformation().positionCache());
            Vector target = j_i.locationWithCache(_context.worldTarget().position());
            j_i.rotateAndUpdateCache(applyCCD(j_i, endEffector, target, true), false, _context.endEffectorInformation()); //Apply local rotation
            return;
        }

        NodeInformation j_i1 = _context.usableChainInformation().get(i + 1);
        j_i1.updateCacheUsingReference();
        if(j_i.node().constraint() == null && !(j_i.node().constraint() instanceof Hinge)){
            return;
        }
        if(j_i1.node().constraint() == null && !(j_i1.node().constraint() instanceof Hinge)){
            return;
        }

        Hinge h_i = (Hinge) j_i.node().constraint();
        Hinge h_i1 = (Hinge) j_i1.node().constraint();

        //1. Determine the normal of the working plane (j_i frame coordinate system)
        Vector normal = h_i.restRotation().rotate(new Vector(0,0,1));

        if(_log) {
            System.out.println("*-*-*-*-*-*-*-*-*-*");
            System.out.println("On i : " + i);
            System.out.println("normal " + normal);
        }

        normal = Quaternion.compose(j_i.node().rotation().inverse(), h_i.idleRotation()).rotate(normal);
        normal.normalize();

        vectors.put("normal", new Pair<>(j_i.node().position(), j_i.node().worldLocation(Vector.multiply(normal, j_i1.node().translation().magnitude()))));


        //2. Project all interesting coordinates in the plane defined by normal vector and whose origin is j_i (0,0,0)
        Vector v_i1 = Vector.projectVectorOnPlane(j_i.locationWithCache(j_i1.positionCache()), normal);
        Vector v_eff = Vector.projectVectorOnPlane(j_i.locationWithCache(_context.endEffectorInformation().positionCache()), normal);

        Vector offset = Vector.subtract(j_i.locationWithCache(_context.endEffectorInformation().positionCache()), v_eff);
        Vector v_t = Vector.projectVectorOnPlane(j_i.locationWithCache(_context.worldTarget().position()), normal);

        if(_log) {
            System.out.println("v_i1 " + v_i1);
            System.out.println("v_eff " + v_eff);
            System.out.println("v_t " + v_t);
        }
        //vectors.put("v_i1", new Pair<>(j_i.node().position(), j_i.node().worldLocation(v_i1)));
        //vectors.put("v_eff", new Pair<>(j_i.node().position(), j_i.node().worldLocation(v_eff)));
        //vectors.put("v_t", new Pair<>(j_i.node().position(), j_i.node().worldLocation(v_t)));


        //3. Find the limits of j_i1 on new space  TODO: REMOVE redundant operations

        //-------------------------------------------
        //-------------------------------------------
        //3.1 find the vectors that define the hinge limits
        Quaternion current_1 = j_i1.node().rotation().get(); //w.r.t hinge reference
        current_1 = Quaternion.compose(h_i1.orientation().inverse(), current_1);
        Vector rotationAxis = new Vector(current_1._quaternion[0], current_1._quaternion[1], current_1._quaternion[2]);
        rotationAxis = Vector.projectVectorOnAxis(rotationAxis, new Vector(0, 0, 1));
        //Get rotation component on Axis direction w.r.t reference
        Quaternion rotationTwist = new Quaternion(rotationAxis.x(), rotationAxis.y(), rotationAxis.z(), current_1.w());
        float deltaAngle = rotationTwist.angle();
        if (rotationAxis.dot(new Vector(0, 0, 1)) < 0) deltaAngle *= -1;
        float change = deltaAngle;


        float theta_1_max = h_i1.maxAngle() - change;
        float theta_1_min = -h_i1.minAngle() - change;

        if(_log) {
            System.out.println("current : " + Math.toDegrees(change));
            System.out.println("theta_max : " + Math.toDegrees(theta_1_max));
            System.out.println("theta_min : " + Math.toDegrees(theta_1_min));
        }
        //apply constrained rotation
        Quaternion r1 = Quaternion.compose(j_i1.node().rotation().inverse(),
                Quaternion.compose(h_i1.orientation(), Quaternion.compose(new Quaternion(new Vector(0, 0, 1), theta_1_max), h_i1.restRotation().inverse())));

        Quaternion r2 = Quaternion.compose(j_i1.node().rotation().inverse(),
                Quaternion.compose(h_i1.orientation(), Quaternion.compose(new Quaternion(new Vector(0, 0, 1), theta_1_min), h_i1.restRotation().inverse())));


        if(_log) {
            System.out.println("h_i1.restRotation() " + h_i1.restRotation().axis() + " a : " + Math.toDegrees(h_i1.restRotation().angle()));
            System.out.println("h_i1.orientation() " + h_i1.orientation().axis() + " a : " + Math.toDegrees(h_i1.orientation().angle()));
            System.out.println("min " + new Quaternion(new Vector(0, 0, 1), theta_1_min).axis() + " a : " + Math.toDegrees(new Quaternion(new Vector(0, 0, 1), theta_1_min).angle()));
            System.out.println("max " + new Quaternion(new Vector(0, 0, 1), theta_1_max).axis() + " a : " + Math.toDegrees(new Quaternion(new Vector(0, 0, 1), theta_1_max).angle()));


            System.out.println("r1 " + r1.axis() + " a : " + Math.toDegrees(r1.angle()));
            System.out.println("r2 " + r2.axis() + " a : " + Math.toDegrees(r2.angle()));
            System.out.println("j_i1 " + j_i1.node().rotation().axis() + " a : " + Math.toDegrees(j_i1.node().rotation().angle()));
        }

        r1 = Quaternion.compose(j_i1.node().rotation(), r1);
        r2 = Quaternion.compose(j_i1.node().rotation(), r2);

        System.out.println("r1 " + r1.axis() + " a : " + Math.toDegrees(r1.angle()));
        System.out.println("r2 " + r2.axis() + " a : " + Math.toDegrees(r2.angle()));
        //end effector in terms of j_i1
        Vector eff_wrt_i1 = j_i1.node().rotation().rotate(j_i1.locationWithCache(_context.endEffectorInformation().positionCache()));
        //vectors.put("eff_wrt_i1", new Pair<>(j_i1.node().position(), j_i.node().worldLocation(eff_wrt_i1)));


        Vector v_max = r1.rotate(eff_wrt_i1);
        //vectors.put("v_max", new Pair<>(j_i1.node().position(), j_i.node().worldLocation(v_max)));

        Vector v_min = r2.rotate(eff_wrt_i1);
        //vectors.put("v_min", new Pair<>(j_i1.node().position(), j_i.node().worldLocation(v_min)));

        if(_log) {
            System.out.println("--- eff wrt i1 (v neutro) " + eff_wrt_i1);
            System.out.println("--- v_max " + v_max);
            System.out.println("--- v_min " + v_min);
            System.out.println("--- angle w.r.t max : " + Math.toDegrees(Vector.angleBetween(v_max, eff_wrt_i1)));
            System.out.println("--- angle w.r.t min : " + Math.toDegrees(Vector.angleBetween(v_min, eff_wrt_i1)));
        }

        Vector eff_aux = Vector.projectVectorOnPlane(eff_wrt_i1, normal);
        //vectors.put("eff_prj", new Pair<>(j_i1.node().position(), eff_aux));

        if(_log) {
            System.out.println("--- eff wrt i1 (v neutro) prj " + eff_aux);
            System.out.println("--- v_max prj" + Vector.projectVectorOnPlane(v_max, normal));
            System.out.println("--- v_min prj" + Vector.projectVectorOnPlane(v_min, normal));
            System.out.println("--- angle w.r.t max : " + Math.toDegrees(Vector.angleBetween(Vector.projectVectorOnPlane(v_max, normal), eff_aux)));
            System.out.println("--- angle w.r.t min : " + Math.toDegrees(Vector.angleBetween(Vector.projectVectorOnPlane(v_min, normal), eff_aux)));
        }
        //define v_max and v_min w.r.t j_i
        //v_max = j_i1.node().rotation().rotate(v_max);
        //v_min = j_i1.node().rotation().rotate(v_min);

        //-------------------------------------------
        //-------------------------------------------

        //3.2 find the limits in the new plane
        float j_i1_max = Vector.angleBetween(Vector.projectVectorOnPlane(v_max, normal), eff_aux);
        //vectors.put("v_max_prj", new Pair<>(j_i1.node().position(), j_i.node().worldLocation(Vector.projectVectorOnPlane(v_max, normal))));

        float j_i1_min = -Vector.angleBetween(Vector.projectVectorOnPlane(v_min, normal), eff_aux);
        //vectors.put("v_min_prj", new Pair<>(j_i1.node().position(), j_i.node().worldLocation(Vector.projectVectorOnPlane(v_min, normal))));
        //vectors.put(" ", new Pair<>(vectors.get("v_max").getValue(), vectors.get("v_max_prj").getValue()));
        //vectors.put("  ", new Pair<>(vectors.get("v_min").getValue(), vectors.get("v_min_prj").getValue()));
        //vectors.put("   ", new Pair<>(_context.target().position(), vectors.get("v_t").getValue()));

        //j_i1_max = theta_1_max;
        //j_i1_min = theta_1_min;
        if(_log) {
            System.out.println("j_i1_max : " + Math.toDegrees(j_i1_max));
            System.out.println("j_i1_min : " + Math.toDegrees(j_i1_min));
        //check signs
        / * if (normal.dot(Vector.cross(eff_aux, v_max, null)) < 0) {
            float aux = -j_i1_min;
            j_i1_min = -j_i1_max;
            j_i1_max = aux;
        } * /
            System.out.println("j_i1_max : " + Math.toDegrees(j_i1_max));
            System.out.println("j_i1_min : " + Math.toDegrees(j_i1_min));
    }
    //Now we have all information required to determine a solution


    //4. Find the two solutions of the triangulation problem assuming no constraints
    // Vector a = v_i1;
    // Vector b = v_eff;
        b.subtract(a);
                Vector c = v_t;

                //vectors.put("b_pr", new Pair<>( j_i.node().worldLocation(a), j_i.node().worldLocation(b)));
                //vectors.put("c_pr", new Pair<>( j_i.node().worldLocation(a), j_i.node().worldLocation(c)));

                if(_log) {
                System.out.println("Vec a : " + a + "mag" + a.magnitude());
                System.out.println("Vec b : " + b + "mag" + b.magnitude());
                System.out.println("Vec c : " + c + "mag" + c.magnitude());
                }


                float a_mag = a.magnitude(), b_mag = b.magnitude(), c_mag = c.magnitude();
                float angle, angle_1, angle_2;

                Vector a_aux = Vector.multiply(a, -1);
                if (a_mag + b_mag <= c_mag) {
                if(_log) System.out.println("---case1 : extend chain!!! on node : " + i);
                //Chain must be extended as much as possible
                angle = (float) (Math.acos(Vector.dot(a_aux, b) / (a_mag * b_mag)));

                if(Vector.dot(Vector.cross(a_aux, b, null), normal) < 0){
        angle = -angle;
        }

        if(_log) {
        System.out.println("current : " + (Math.toDegrees(angle)));
        System.out.println("expected : " + (Math.toDegrees(Math.PI)));
        }

        angle = (float) (Math.PI) - angle;
        angle_1 = angle;
        angle_2 =  -(float)(2 * Math.PI - angle);
        } else if (c_mag < Math.abs(a_mag - b_mag)) {
        if(_log) System.out.println("---case2 : contract chain!!! on node : " + i);
        //Chain must be contracted as much as possible
        angle = (float) (Math.acos(Vector.dot(a_aux, b) / (a_mag * b_mag)));

        if(Vector.dot(Vector.cross(a_aux, b, null), normal) < 0){
        angle = -angle;
        }

        if(_log) {
        System.out.println("current : " + (Math.toDegrees(angle)));
        System.out.println("expected : " + 0);
        }
        angle_1 = -angle;
        angle_2 = (float)(2*Math.PI - angle);

        } else {
        //Apply law of cosines
        float expected = (float)Math.acos(-(c_mag * c_mag - a_mag * a_mag - b_mag * b_mag) / (2f * a_mag * b_mag));
        float current = (float) (Math.acos(Vector.dot(a_aux, b) / (a_mag * b_mag)));
        if(Vector.dot(Vector.cross(a_aux, b, null), normal) < 0){
        current = -current;
        }


        if(_log) {
        System.out.println("current : " + Math.toDegrees(current));
        System.out.println("expected : " + Math.toDegrees(expected));
        }

        angle_1 = expected - current;
        angle_2 = -expected - current;

        }

        if(_log) {
        System.out.println("angle_1 : " + Math.toDegrees(angle_1));
        System.out.println("angle_2 : " + Math.toDegrees(angle_2));
        }

        //Normalize angles btwn -PI and PI
        angle_1 = (float) (angle_1 - 2 * Math.PI * Math.floor((angle_1 + Math.PI) / (2 * Math.PI)));
        angle_2 = (float) (angle_2 - 2 * Math.PI * Math.floor((angle_2 + Math.PI) / (2 * Math.PI)));

        if(_log) {
        System.out.println("angle_1 : " + Math.toDegrees(angle_1));
        System.out.println("angle_2 : " + Math.toDegrees(angle_2));
        }
        //------------------------------------------

        //5. apply constraints
        if (j_i1_min > angle_1 || angle_1 > j_i1_max) {
        angle_1 = angle_1 < 0 ? (float) (angle_1 + 2 * Math.PI) : angle_1;
        angle_1 = angle_1 - j_i1_max < (float) (j_i1_min + 2 * Math.PI) - angle_1 ? j_i1_max : j_i1_min;
        }

        if (j_i1_min > angle_2 || angle_2 > j_i1_max) {
        angle_2 = angle_2 < 0 ? (float) (angle_2 + 2 * Math.PI) : angle_2;
        angle_2 = angle_2 - j_i1_max < (float) (j_i1_min + 2 * Math.PI) - angle_2 ? j_i1_max : j_i1_min;
        }

        //angle_1 = angle_1 < j_i1_min ? j_i1_min : angle_1 > j_i1_max ? j_i1_max : angle_1;
        //angle_2 = angle_2 < j_i1_min ? j_i1_min : angle_2 > j_i1_max ? j_i1_max : angle_2;

        if(_log) {
        System.out.println("c angle_1 : " + Math.toDegrees(angle_1));
        System.out.println("c angle_2 : " + Math.toDegrees(angle_2));
        }

        //6. Find the virtual new position of end effector

        //Keep state of end effector
        //6.1 Find eff final position applying angle_1 solution
        Quaternion delta_1 = new Quaternion(normal, angle_1);
        //Assume that the end effector reach the desired position
        Vector v_eff_1 = delta_1.rotate(b);
        v_eff_1.add(a);

        if(_log) System.out.println("v_eff_1 : " + v_eff_1);

        //vectors.put("v_eff_1", new Pair<>( j_i.node().worldLocation(a), j_i.node().worldLocation(v_eff_1)));

        //Apply ccd to new configuration on v_eff_1 (CCD is applied three times)
        Quaternion s1 = applyCCD(j_i, v_eff_1, v_t, false);
        //update real target
        Vector sol_1 = j_i1.node().rotation().inverseRotate(Vector.subtract(s1.inverseRotate(j_i.locationWithCache(_context.worldTarget().position())), j_i1.node().translation()));
        //express sol_1 w.r.t j_i1
        //Apply ccd on j_i1
        Quaternion s1_1 = applyCCD(j_i1, j_i1.locationWithCache(_context.endEffectorInformation().positionCache()), sol_1, false);
        //update eff
        sol_1 = s1_1.rotate(j_i1.locationWithCache(_context.endEffectorInformation().positionCache()));
        //express w.r.t j_i
        sol_1 = s1.rotate(Vector.add(j_i1.node().translation(), j_i1.node().rotation().rotate(sol_1)));

        //TODO: Apply CCD a third time
        System.out.println("sol_1 : " + sol_1);
        vectors.put("sol_1", new Pair<>( j_i.node().position(), j_i.node().worldLocation(sol_1)));

        //6.1 Find eff final position applying angle_2 solution
        Quaternion delta_2 = new Quaternion(normal, angle_2);
        //Assume that the end effector reach the desired position
        Vector v_eff_2 = delta_2.rotate(b);
        v_eff_2.add(a);

        System.out.println("v_eff_2 : " + v_eff_2);

        //vectors.put("v_eff_2", new Pair<>( j_i.node().worldLocation(a), j_i.node().worldLocation(v_eff_2)));


        //Apply ccd to new configuration on v_eff_2 (CCD is applied three times)
        Quaternion s2 = applyCCD(j_i, v_eff_2, v_t, false);
        //update real eff position
        Vector sol_2 = j_i1.node().rotation().inverseRotate(Vector.subtract(s2.inverseRotate(j_i.locationWithCache(_context.worldTarget().position())), j_i1.node().translation()));
        //express sol_1 w.r.t j_i1
        //Apply ccd on j_i1
        Quaternion s2_1 = applyCCD(j_i1, j_i1.locationWithCache(_context.endEffectorInformation().positionCache()), sol_2, false);
        //update eff
        sol_2 = s2_1.rotate(j_i1.locationWithCache(_context.endEffectorInformation().positionCache()));
        //express w.r.t j_i
        sol_2 = s2.rotate(Vector.add(j_i1.node().translation(), j_i1.node().rotation().rotate(sol_2)));

        //TODO: Apply CCD a third time
        System.out.println("sol_2 : " + sol_2);
        vectors.put("sol_2", new Pair<>( j_i.node().position(), j_i.node().worldLocation(sol_2)));

        System.out.println("v_t : " + v_t);
        //System.out.println("s1 : " + s1.axis() + " a : " + Math.toDegrees(s1.angle()));
        //System.out.println("s2 : " + s2.axis() + " a : " + Math.toDegrees(s2.angle()));
        System.out.println("dist_1 : " + Vector.distance(sol_1, j_i1.locationWithCache(_context.worldTarget().position())) );
        System.out.println("dist_2 : " + Vector.distance(sol_2, j_i1.locationWithCache(_context.worldTarget().position())) );

        if(Vector.distance(sol_1, j_i.locationWithCache(_context.worldTarget().position())) < Vector.distance(sol_2, j_i.locationWithCache(_context.worldTarget().position()))){
        System.out.println("dist_1 : " + Vector.distance(sol_1, j_i.locationWithCache(_context.worldTarget().position())));
        j_i.rotateAndUpdateCache(s1, false, _context.endEffectorInformation()); //Apply local rotation
        //j_i1.updateCacheUsingReference();
        //j_i1.rotateAndUpdateCache(s1_1, false, _context.endEffectorInformation()); //Apply local rotation
        } else{
        System.out.println("dist_2 : " + Vector.distance(sol_2, j_i.locationWithCache(_context.worldTarget().position())));
        j_i.rotateAndUpdateCache(s2, false, _context.endEffectorInformation()); //Apply local rotation
        //j_i1.updateCacheUsingReference();
        //j_i1.rotateAndUpdateCache(s2_1, false, _context.endEffectorInformation()); //Apply local rotation
        }


        }
    * */

    /*Old multiple solutions
    *         /*
        //Find the virtual new position of end effector
        Vector v_eff_1 = solutions[0].rotate(eff_wrt_j_i1);
        //Express v_eff_1 w.r.t j_i
        v_eff_1 = Vector.add(j_i1.node().translation(), j_i1.node().rotation().rotate(v_eff_1));
        if(_log) System.out.println("v_eff_1 : " + v_eff_1);

        //vectors.put("v_eff_1", new Pair<>( j_i.node().worldLocation(a), j_i.node().worldLocation(v_eff_1)));

        //Apply ccd to new configuration on v_eff_1 (CCD is applied three times)
        Quaternion s1 = applyCCD(j_i, v_eff_1, j_i.locationWithCache(_context.worldTarget().position()), true);
        //update real target
        Vector sol_1 = j_i1.node().rotation().inverseRotate(Vector.subtract(s1.inverseRotate(j_i.locationWithCache(_context.worldTarget().position())), j_i1.node().translation()));
        //express sol_1 w.r.t j_i1
        //Apply ccd on j_i1
        Quaternion s1_1 = applyCCD(j_i1, j_i1.locationWithCache(_context.endEffectorInformation().positionCache()), sol_1, true);
        //update eff
        sol_1 = s1_1.rotate(j_i1.locationWithCache(_context.endEffectorInformation().positionCache()));
        //express w.r.t j_i
        sol_1 = s1.rotate(Vector.add(j_i1.node().translation(), j_i1.node().rotation().rotate(sol_1)));

        System.out.println("sol_1 : " + sol_1);
        vectors.put("sol_1", new Pair<>( j_i.node().position(), j_i.node().worldLocation(sol_1)));


        //Find the virtual new position of end effector
        Vector v_eff_2 = solutions[1].rotate(j_i1.locationWithCache(_context.endEffectorInformation()));
        //Express v_eff_1 w.r.t j_i
        v_eff_2 = Vector.add(j_i1.node().translation(), j_i1.node().rotation().rotate(v_eff_2));
        System.out.println("v_eff_2 : " + v_eff_2);
        //vectors.put("v_eff_2", new Pair<>( j_i.node().worldLocation(a), j_i.node().worldLocation(v_eff_2)));

        //Apply ccd to new configuration on v_eff_2 (CCD is applied three times)
        Quaternion s2 = applyCCD(j_i, v_eff_2, j_i.locationWithCache(_context.worldTarget().position()), true);
        //update real eff position
        Vector sol_2 = j_i1.node().rotation().inverseRotate(Vector.subtract(s2.inverseRotate(j_i.locationWithCache(_context.worldTarget().position())), j_i1.node().translation()));
        //express sol_1 w.r.t j_i1
        //Apply ccd on j_i1
        Quaternion s2_1 = applyCCD(j_i1, j_i1.locationWithCache(_context.endEffectorInformation().positionCache()), sol_2, true);
        //update eff
        sol_2 = s2_1.rotate(j_i1.locationWithCache(_context.endEffectorInformation().positionCache()));
        //express w.r.t j_i
        sol_2 = s2.rotate(Vector.add(j_i1.node().translation(), j_i1.node().rotation().rotate(sol_2)));

        System.out.println("sol_2 : " + sol_2);
        vectors.put("sol_2", new Pair<>( j_i.node().position(), j_i.node().worldLocation(sol_2)));

        //System.out.println("v_t : " + v_t);
        //System.out.println("s1 : " + s1.axis() + " a : " + Math.toDegrees(s1.angle()));
        //System.out.println("s2 : " + s2.axis() + " a : " + Math.toDegrees(s2.angle()));
        System.out.println("dist_1 : " + Vector.distance(sol_1, j_i1.locationWithCache(_context.worldTarget().position())) );
        System.out.println("dist_2 : " + Vector.distance(sol_2, j_i1.locationWithCache(_context.worldTarget().position())) );

        if(Vector.distance(sol_1, j_i.locationWithCache(_context.worldTarget().position())) < Vector.distance(sol_2, j_i.locationWithCache(_context.worldTarget().position()))){
            System.out.println("dist_1 : " + Vector.distance(sol_1, j_i.locationWithCache(_context.worldTarget().position())));
            j_i.rotateAndUpdateCache(s1, false, _context.endEffectorInformation()); //Apply local rotation
            j_i1.updateCacheUsingReference();
            j_i1.rotateAndUpdateCache(s1_1, false, _context.endEffectorInformation()); //Apply local rotation
        } else{
            System.out.println("dist_2 : " + Vector.distance(sol_2, j_i.locationWithCache(_context.worldTarget().position())));
            j_i.rotateAndUpdateCache(s2, false, _context.endEffectorInformation()); //Apply local rotation
            j_i1.updateCacheUsingReference();
            j_i1.rotateAndUpdateCache(s2_1, false, _context.endEffectorInformation()); //Apply local rotation
        }
    */
