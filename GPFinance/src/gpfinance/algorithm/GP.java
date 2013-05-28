package gpfinance.algorithm;

import gpfinance.U;
import gpfinance.algorithm.interfaces.SelectionStrategy;
import gpfinance.algorithm.interfaces.MutationStrategy;
import gpfinance.algorithm.interfaces.CrossoverStrategy;
import gpfinance.datatypes.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * @date 2013-06-01
 * @author Simon van Dyk, Stuart Reid
 */
public class GP {
    /* Financial Data */
    public static ArrayList<Security> securities = new ArrayList();

    private static final int NUM_MUTATIONS = 6;
    private static final int RESOLUTION = 50;
            
    /* Control Parameters */
    private int generations = 2000;
    private int populationSize = 100;
    private ArrayList<Individual> population = new ArrayList(populationSize);
    //                                      {grow,  trunc, indicator, leaf, inequality, gauss}
    private double[] initialMutationRates = {0.5,   0.0,   0.75,      0.85, 0.75,       0.95};
    //                                      {grow,  trunc, indicator, leaf, inequality, gauss}
    private double[] finalMutationRates =   {0.1,   0.3,   0.3,       0.5,  0.3,        0.4};
    private double initialCrossoverProb = 0.6;
    private double finalCrossoverProb = 0.4;
    private char analysisType = 'F';
    /* Strategy control parameters */
    private double[] restartRates = {0.4, 0.02};
    
    /* Strategies */
    private InitializationStrategy initializationStrategy = new InitializationStrategy(analysisType);
    private SelectionStrategy populationSelectionStrategy = new StochasticMuLambdaSelectionStrategy(restartRates); // elitism
    private SelectionStrategy reproductionSelectionStrategy = new RankBasedSelectionStrategy();
    private CrossoverStrategy crossoverStrategy = new SexualCrossoverStrategy(initialCrossoverProb, finalCrossoverProb);
    private MutationStrategy mutationStrategy = new TreeMutationStrategy(initialMutationRates, finalMutationRates);

    // generate constructors once all instance variables defined
    public GP() { /* Create GP with default parameters */ }

    public GP(HashMap options) {
        // Set class values if they exist in the hash
        if (options.containsKey("type")) {
            this.analysisType = (options.get("type") == "fundamental") ? 'F' : 'T';
        }
        if (options.containsKey("generations")) {
            this.generations = Integer.parseInt((String) options.get("generations"));
        }
        if (options.containsKey("population")) {
            this.populationSize = Integer.parseInt((String) options.get("population"));
        }
        if (options.containsKey("crossoverRate")) {
            String[] rates = ((String) options.get("crossoverRate")).split(":");
            this.initialCrossoverProb = Double.parseDouble(rates[0]);
            this.finalCrossoverProb = Double.parseDouble(rates[1]);
        }
        if (options.containsKey("mutationRateStart")) {
            String[] rates = ((String) options.get("mutationRateStart")).split(":");
            this.initialMutationRates = new double[NUM_MUTATIONS];
            for (int i = 0; i < rates.length; ++i) {
                initialMutationRates[i] = Double.parseDouble(rates[i]);
            }
        }
        if (options.containsKey("mutationRateEnd")) {
            String[] rates = ((String) options.get("mutationRateEnd")).split(":");
            this.finalMutationRates = new double[NUM_MUTATIONS];
            for (int i = 0; i < rates.length; ++i) {
                finalMutationRates[i] = Double.parseDouble(rates[i]);
            }
        }
        // Set class strategies if they exist in the hash
        if (options.containsKey("populationSelection")) {
            String tmp = ((String) options.get("populationSelection"));
            switch (tmp) {
                case "mulambda":
                    this.populationSelectionStrategy = new MuLambdaSelectionStrategy();
                    break;
                case "rankbased":
                    this.populationSelectionStrategy = new RankBasedSelectionStrategy();
                    break;
                default:
                    this.populationSelectionStrategy = new RandomSelectionStrategy();
                    break;
            }
        }
        if (options.containsKey("reproductionSelection")) {
            String tmp = ((String) options.get("reproductionSelection"));
            switch (tmp) {
                case "rankbased":
                    this.populationSelectionStrategy = new RankBasedSelectionStrategy();
                    break;
                case "mulambda":
                    this.populationSelectionStrategy = new MuLambdaSelectionStrategy();
                    break;
                default:
                    this.populationSelectionStrategy = new RandomSelectionStrategy();
                    break;
            }
        }

        // Create the rest of the variables and strategies
        population = new ArrayList(populationSize);
        initializationStrategy = new InitializationStrategy(analysisType);
        crossoverStrategy = new SexualCrossoverStrategy(initialCrossoverProb, finalCrossoverProb);
        mutationStrategy = new TreeMutationStrategy(initialMutationRates, finalMutationRates);
    }

    public void run() {
        // Initialize population
        initializationStrategy.init(population, populationSize);

        // For each generation
        int gen = 0;
        do {
            // Measure individuals
            measure(population, gen);

            // Clone previous generation P
            ArrayList<Individual> previousPopulation = new ArrayList();
            for (int i = 0; i < population.size(); ++i) {
                previousPopulation.add(population.get(i).clone());
            }

            // Selection for reproduction
            //TODO: Agree on selection here with Stu
            ArrayList<Individual> candidatePopulation = reproductionSelectionStrategy.select(population, population.size() / 2);

            // Reproduction producing P'
            double progress = ((double) gen / (double) generations);
            ArrayList<Individual> crossoverOffspring = crossoverStrategy.crossover(candidatePopulation, progress);
            measure(crossoverOffspring, gen);

            // Mutation producing P''
            ArrayList<Individual> mutationOffspring = mutationStrategy.mutate(crossoverOffspring, progress);
            measure(mutationOffspring, gen);
            
            // Select P(t+1) from union of offspring: P U P'' -- should we select from P U P' U P''
            //previousPopulation.addAll(crossoverOffspring); //crossed over -- should we include these, even?
            previousPopulation.addAll(mutationOffspring);  //crossed over and mutated
            population = populationSelectionStrategy.selectDynamic(previousPopulation, populationSize, progress);

            // Advance to next generation
            ++gen;
            if (gen % RESOLUTION == 0){
                U.m(gen + ":\t" + getBest().getFitness());
            }
            //printBest();
        } while (gen < generations);

        U.m("\n\n****************************************  " + "RUN COMPLETE" + "  ****************************************\n\n");
        printBest();
    }

    private void measure(ArrayList<Individual> individuals, int generation) {
        for (Individual individual : individuals) {
            individual.measure(generation, securities);
        }
    }
    
    private Individual getBest() {
        Collections.sort(population, Individual.DescendingFitness);
        return population.get(0);
    }

    private void printBest() {
        U.m("Best f(): " + getBest().getFitness());
    }

    private void printPopulationFitnesses(ArrayList<Individual> individuals) {
        Collections.sort(individuals, Individual.DescendingFitness);
        for (Individual i : individuals) {
            U.p(i.getFitness() + ", ");
        }
        U.pl();
    }
}
