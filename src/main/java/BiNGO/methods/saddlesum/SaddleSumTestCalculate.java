/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package BiNGO.methods.saddlesum;

import BiNGO.interfaces.CalculateTestTask;
import BiNGO.interfaces.DistributionCount;
import BiNGO.methods.AbstractCalculateTestTask;
import cytoscape.task.TaskMonitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
//import java.lang.Math;

/**
 *
 * @author pmoreno
 */
public class SaddleSumTestCalculate extends AbstractCalculateTestTask implements CalculateTestTask {

    private Map<String, Double> weights;
    private Map<Integer, Double> mapWeightSum;
    // private HashMap<Integer, Double> mapD1K;  //? notwendig
    // private HashMap<Integer, Double> mapD2K;  //?
    private Map<Integer, Double> lambdas;
    // private Map<Integer, Double> pVal;
    // private HashMap<Integer, Double> zs;

    /**
     * constructor with as argument the selected cluster and the annotation, ontology and alpha.
     */
    public SaddleSumTestCalculate(DistributionCount dc) {
        dc.calculate();
        this.mapSmallN = dc.getMapSmallN();     //ID jedes knotens der Ontologie mapped auf Anzahl der Blätter darunter
        this.mapSmallX = dc.getMapSmallX();     // ID jedes knotens der Ontologie, die Blätter aus dem Sample haben, mapped auf die Anzahl der Blätter aus dem Sample
        this.mapBigN = dc.getMapBigN();         // mapBigN und mapBigX
        this.mapBigX = dc.getMapBigX();         //notwendig?
        this.maxValue = mapSmallX.size();
        weights = dc.getWeights();
        mapWeightSum = dc.getMapWeights();
        this.title = "Calculating Saddle Sum Test";
    }

    /**
     * method that redirects the calculation of hypergeometric distribution.
     */
    @Override
    public void calculate() {

        SaddleSumDistribution hd;
        significanceTestMap = new HashMap();
        // Here the saddleSum first part should be computed, which uses all the scores
        // from the different participating elements.

        //Berechnung nur für Knoten aus mapSmallX notwendig        
        iterateNewton();
        lugRice();
    }

    public double calcSaddlepoint(int id, double lambda, int eq) {          //Fkt. zur Berechnung der ersten Ableitung von K'(t)
        //Variable eq zur Bestimmumg, ob d1K benötigt oder d2K

        HashSet<String> weightSet = new HashSet(weights.keySet());

        double tmp = 0.0;
        double wmax = Collections.max(weights.values());
        int N = (Integer) Collections.max(mapSmallN.values());             //Anzahl aller Blätter der Ontologie
        //int N = (Integer)mapSmallN.get(36342) + (Integer)mapSmallN.get(50906) + (Integer)mapSmallN.get(24431);
        //System.out.println(N);
        //System.out.println(wmax);       
        double rhoT = 0.0;      //Nenner von K'(t) 
        double D1RhoT = 0.0;    //Zähler von K'(t)
        double D2RhoT = 0.0;
        double sum = mapWeightSum.get(id);

        double diff = N - weights.size();


        for (String idw : weightSet) {

            double w = weights.get(idw);

            tmp = Math.pow(Math.E, lambda * (w - wmax));

            rhoT += tmp;

            tmp *= w;

            D1RhoT += tmp;

            tmp *= w;

            D2RhoT += tmp;
        }

        rhoT += diff * Math.pow(Math.E, lambda * (-wmax));


        double D1K = D1RhoT / rhoT;

        double D2K = D2RhoT / rhoT - D1K * D1K;

        int m = (Integer) mapSmallN.get(id);



        if (eq == 1) {
            return m * D1K - sum;
        }           //equation (3) of the paper
        if (eq == 2) {
            return m * D2K;
        }                 //
        if (eq == 3) {
            double expH = rhoT * Math.pow(Math.E, lambda * (wmax - D1K)) / N;
            return expH;
        }
        if (eq == 4) {
            double C = 2 * lambda * Math.sqrt(D2K);
            return C;
        }
        if (eq == 5) {
            double D = -Math.sqrt(2) * Math.sqrt(lambda * (D1K - wmax) - Math.log(rhoT) + Math.log(N));
            //System.out.println("lambda:\t"+lambda);
            //System.out.println("D1K:\t"+D1K);
            //System.out.println("wmax:\t"+wmax);
            //System.out.println("lambda * (D1K-wmax):\t"+lambda * (D1K-wmax));
            //System.out.println("Math.log(rhoT):\t" + Math.log(rhoT));
            //System.out.println("Math.log(N):\t"+Math.log(N));
            return D;
        } else {
            return 0.0;
        }
    }

    public double bisect(int id, double intervalStart, double intervalEnd) {

        double a = intervalStart;                             //Startwerte für lambda
        double b = intervalEnd;

        int n = 0;
        double c = 0.0;
        //System.out.println(n);
        //System.out.println(mapWeightSum.get(id)+"\t"+mapSmallN.get(id));
        //System.out.println(calcSaddlepoint(id,a,1)+ "\t"+calcSaddlepoint(id,b,1)); 
        if (calcSaddlepoint(id, a, 1) * calcSaddlepoint(id, b, 1) <= 0) {      //Test auf Ungleichheit der Vorzeichen

            while (n <= 100) {                                       //max_iteration = 1000 -> zu klein? zu groß?

                n++;
                c = (a + b) / 2;

                if ((calcSaddlepoint(id, c, 1) == 0.0) || ((b - a) < 0.05)) {        //Nullstelle gefunden oder Toleranz (hier = 0.05, andere Toleranz?) erreicht
                    //System.out.println(calcSaddlepoint(id,c,1));

                    // System.out.println(c);
                    return c;
                }


                if (calcSaddlepoint(id, c, 1) * calcSaddlepoint(id, a, 1) > 0) {        //Test auf Gleichheit der Vorzeichen
                    a = c;
                } else {
                    b = c;
                }
            }
        }
        if (calcSaddlepoint(id, a, 1) == calcSaddlepoint(id, b, 1)) {
            return 0.0;
        } else {
            c = bisect(id, b, b + 10.0);
            //return 0.0;
        }
        return c;
    }

    public double newton(int id, double lambda) {

        double lmbdAlt = lambda;
        double lmbdNeu = 0.0;

        while (Math.abs(lmbdAlt - lmbdNeu) >= 0.01) {                          //tol für Nullstelle?, tol als private static epsilon


            double zaehler = calcSaddlepoint(id, lmbdAlt, 1);
            double nenner = calcSaddlepoint(id, lmbdAlt, 2);

            lmbdNeu = lmbdAlt - zaehler / nenner;

            lmbdAlt = lmbdNeu;

        }


        return lmbdNeu;
    }

    public void iterateNewton() {

        lambdas = new HashMap<Integer, Double>();

        HashSet<Integer> set = new HashSet(mapWeightSum.keySet());      //Iterator über alle ChEBI-IDs aus mapWeightSum

        for (Integer id : set) {                                        //Schleife über alle Einträge in mapWeightSum
            //System.out.println(mapWeightSum.get(id));
            //if((Integer)mapSmallN.get(id)<(Integer) Collections.max(mapSmallN.values())){
            double d = bisect(id, 0.00001, 5.0);                             //Bisektion für aktuelles Array
            //if(d != 0.0){
            if ((calcSaddlepoint(id, d, 1)) == 0.0) {
                lambdas.put(id, d);
            } else {
                double n = newton(id, d);                                    //Erg. der Bisektion ist Start für Newton, n = gefundenes lambda
                //System.out.println(calcSaddlepoint(id,n,1));                //gefundene Nullstelle für erg von Newton

                lambdas.put(id, n);
            }   // }                                      // map mit lmabdas füllen, ChEBI-ID->lambda
        }
        //System.out.println(mapWeightSum.size());
        //System.out.println(lambdas);
    }

    public void lugRice() {

        double expH;
        double C;
        double D;
        double lmbd;
        double phi;
        int m;
        double pValue;

        significanceTestMap = new HashMap<Integer, Double>();

        HashSet<Integer> set = new HashSet(lambdas.keySet());

        for (Integer id : set) {


            m = (Integer) mapSmallX.get(id);

            //System.out.println("______\nm:"+"\t"+m);
            lmbd = lambdas.get(id);

            expH = calcSaddlepoint(id, lmbd, 3);
            C = calcSaddlepoint(id, lmbd, 4);
            D = calcSaddlepoint(id, lmbd, 5);

            //System.out.println("D:\t"+D);

            phi = Math.sqrt(2 / Math.PI) * Math.pow(expH, m);

            double z = ((D * Math.sqrt(m)) / Math.sqrt(2));
            //System.out.println("phi:\t"+phi);
            if (D * Math.sqrt(m) < -1) {
                double ndtr = (1 + ErrorFunction.erf(z)) / 2;
                //System.out.println(ndtr);
                //System.out.println(phi / C / Math.sqrt(m));
                //System.out.println(phi / D / Math.sqrt(m));
                pValue = ((1 + ErrorFunction.erf(z)) / 2) + (phi / C / Math.sqrt(m)) + (phi / D / Math.sqrt(m) / 2);

                significanceTestMap.put(id, pValue);
            }

        }
    }

    //public Map<Integer, Double> getPValueMap() {
    //    return this.significanceTestMap;
    //}

    public void setTaskMonitor(TaskMonitor tm) throws IllegalThreadStateException {
        //throw new UnsupportedOperationException("Not supported yet.");
    }
}
