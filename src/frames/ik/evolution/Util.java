package frames.ik.evolution;

import frames.core.Frame;
import frames.ik.evolution.operator.Operator;
import frames.primitives.Quaternion;
import frames.primitives.Vector;

import java.util.*;

/**
 * Created by sebchaparr on 28/10/18.
 */
public class Util {
    protected static Random _random;

    public static List<Individual> sort(boolean inPlace, boolean reverse, List<Individual> population){
        final int sign = reverse ? 1 : -1;
        List<Individual> sorted = population;
        if(!inPlace) sorted = new ArrayList<Individual>(population);
        sorted.sort(new Comparator<Individual>() {
            @Override
            public int compare(Individual o1, Individual o2) {
                if(o1.fitness() < o2._fitness){
                    return -sign;
                } else if(o1.fitness() > o2.fitness()){
                    return sign;
                } else{
                    return 0;
                }
            }
        });
        if(inPlace) population = sorted;
        return sorted;
    }

    public static List<Individual> generatePopulation(List<Frame> structure, int n, float max_angle){
        List<Individual> population = new ArrayList<>();
        List<Frame> copy_structure = new ArrayList<>();
        Individual original = new Individual(structure);
        for(int i = 0; i < n; i++){
            Individual individual = original.clone();
            for(Frame frame : individual.structure()){
                float roll = (float) Math.toRadians(max_angle * _random.nextFloat());
                float pitch = (float) Math.toRadians(max_angle * _random.nextFloat());
                float yaw = (float) Math.toRadians(max_angle * _random.nextFloat());
                frame.setRotation(new Quaternion(roll, pitch, yaw ));
            }
            population.add(individual);
        }
        return population;
    }

    public static List<Individual> generatePopulation(List<Frame> structure, int n){
        return generatePopulation(structure, n, 5);
    }

    public static List<Individual> concatenate(List<Individual>... lists){
        List<Individual> concatenation = new ArrayList<>();
        for(List<Individual> list : lists){
            concatenation.addAll(list);
        }
        return concatenation;
    }

    public static void printList(List<Individual> list){
        System.out.print("[ ");
        for(Individual individual : list){
            System.out.print( individual.fitness() + ", ");
        }
        System.out.println(" ]");
    }

    public static void main(String[] args){
        //create _random Individuals
        Random r = new Random();
        List<Individual> l = new ArrayList<Individual>();
        for(int i = 0; i < 15; i++){
            Individual ind = new Individual(null);
            ind.setFitness(r.nextFloat()*100);
            l.add(ind);
        }
        printList(l);
        List<Individual> l2 = sort(true, true, l);
        printList(l);
        printList(l2);

    }
}
