package ch.idsia.ai.agents.NEATMario;

import ch.idsia.ai.Evolvable;
import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.mario.environments.Environment;
import ch.idsia.tools.EvaluationInfo;
import ch.idsia.tools.EvaluationOptions;
import ch.idsia.ai.tasks.Task;
import ch.idsia.tools.Evaluator;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.Random;

import static java.lang.Math.abs;
import static java.lang.Math.ceil;


/**
 * Created by Owner on 12/8/2016.
 */
public class NEATAgent implements Agent, Evolvable  {

    protected boolean action[];
    protected String name;
    double fitest;
    public Population population;
    Evaluator evaluator;



    public class Population {

        public class Species {
            public Species(Species s) {
                for(Map.Entry<Dendrite, Set<Axon>> e : s.species.entrySet())
                {
                    for(Axon a : e.getValue())
                        addMember(e.getKey().loc.x, e.getKey().loc.y, e.getKey().label, a.action);
                }
            }

            public Species(Map.Entry<Dendrite, Axon>... members) {
                for (int i = 0; i < members.length; ++i)
                    addMember(members[i].getKey().loc.x, members[i].getKey().loc.y, members[i].getKey().label, members[i].getValue().action);
            }

            public void print() {
                for(Map.Entry<Dendrite, Set<Axon>> e : species.entrySet())
                {
                    for(Axon a : e.getValue())
                    {
                        System.out.print(e.getKey().label + " (" + e.getKey().loc.x  + ", " + e.getKey().loc.y + ") -> ");
                        switch(a.action)
                        {
                            case Mario.KEY_DOWN:
                                System.out.print("DOWN");
                                break;
                            case Mario.KEY_LEFT:
                                System.out.print("LEFT");
                                break;
                            case Mario.KEY_RIGHT:
                                System.out.print("RIGHT");
                                break;
                            case Mario.KEY_SPEED:
                                System.out.print("SPEED");
                                break;
                            case Mario.KEY_JUMP:
                                System.out.print("JUMP");
                                break;
                        }
                        System.out.print("\t");
                    }
                }
                System.out.println();
            }

            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null) return false;
                return species.equals(((Species) o).species);
            }

            public int hashCode() {
                int result = 3;
                result = 37 * result + species.hashCode();
                return result;
            }

            public class Dendrite {
                public class Coord {
                    public Coord(int x, int y) {
                        this.x = x;
                        this.y = y;
                    }

                    public int x;
                    public int y;
                }

                public void mutate(int xBound, int yBound, int numLabels) {
                    Random rng = new Random();

                    BiFunction<Double, Double, Double> gauss = (stddev, mean) -> rng.nextGaussian() * stddev + mean;
                    Function<Integer, Integer> nextX = (oldX) -> {
                        Double x = gauss.apply(((double)xBound)/6.0, (double)oldX) % ((double)xBound);
                        if(x < 0.0) x += ((double)xBound);
                        return x.intValue();
                    };

                    Function<Integer, Integer> nextY = (oldY) -> {
                        Double y = gauss.apply(((double)yBound)/6.0, (double)oldY) % ((double)yBound);
                        if(y < 0.0) y += ((double)yBound);
                        return y.intValue();
                    };

                    //TODO: MAKE A BETTER MODEL THAT HANDLES LABEL MORE PROPERLY
                    //  Label change chance based on euclidean distance from old loc to new loc
                    //  Chance (if change) of changing to a given label = label frequency
                    //  Further idea: map label frequency to location? Food for thought.
                    Function<Integer, Integer> nextLabel = (oldLabel) -> {
                        double labelChangeChance = 0.1;
                        if(rng.nextDouble() < labelChangeChance) return rng.nextInt(numLabels);
                        return oldLabel;
                    };

                    loc.x = nextX.apply(loc.x);
                    loc.y = nextY.apply(loc.y);
                    label = nextLabel.apply(label);
                }

                public Dendrite(Dendrite o) {
                    this.loc = new Coord(o.loc.x, o.loc.y);
                    this.label = o.label;
                }

                public Dendrite(int x, int y, int label) {
                    this.loc = new Coord(x, y);
                    this.label = label;
                }

                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null) return false;
                    return loc.x == ((Dendrite) o).loc.x && loc.y == ((Dendrite) o).loc.y && label == ((Dendrite) o).label;
                }

                public int hashCode() {
                    int result = 3;
                    result = 37 * result + loc.x;
                    result = 37 * result + loc.y;
                    result = 37 * result + label;
                    return result;
                }

                public Coord loc;
                public int label;
            }

            public class Axon {
                public Axon(int action) {
                    this.action = action;
                }

                public Axon(Axon o) {
                    this.action = o.action;
                }

                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null) return false;
                    return action == ((Axon) o).action;
                }

                public int hashCode() {
                    int result = 3;
                    result = 37 * result + action;
                    return result;
                }

                //TODO: MAKE MUTATE FUNCTION WORK AROUND DENDRITE MUTATE FUNCTION
                //  may require that the mutate functions take extra params to share info
                public void mutate(int numActions) {
                    Random rng = new Random();

                    Function<Integer, Integer> nextAction = (oldAction) -> {
                        double actionChangeChance = 0.2;
                        int actions[] = new int[]{Mario.KEY_JUMP, Mario.KEY_RIGHT, Mario.KEY_SPEED};
                        if(rng.nextDouble() < 0.2) return actions[rng.nextInt(3)];
                        return oldAction;
                    };

                    action = nextAction.apply(action);
                }

                public int action;
            }

            public void mutate(int xBound, int yBound, int numLabels, int numActions, double mutationChance) {
                List<Map.Entry<Dendrite, Axon>> genes = new LinkedList<>();
                for(Map.Entry<Dendrite, Set<Axon>> e : species.entrySet())
                {
                    for(Axon a : e.getValue())
                    {
                        genes.add(new AbstractMap.SimpleEntry<>(e.getKey(), a));
                    }
                }
                species = new HashMap<>();
                for(Map.Entry<Dendrite, Axon> gene : genes)
                {
                    if(new Random().nextDouble() < mutationChance)
                    {
                        gene.getKey().mutate(xBound, yBound, numLabels);
                        gene.getValue().mutate(numActions);
                    }
                    addMember(gene.getKey(), gene.getValue());
                }
            }

            public Set<Axon> addMember(int x, int y, int label, int action) {
                return addMember(new Dendrite(x, y, label), new Axon(action));
            }
            public Set<Axon> addMember(Dendrite d, Axon a) {
                Set<Axon> set = new HashSet<>();
                set.add(a);
                return species.merge(d, set, (i, j) -> {
                    i.addAll(j);
                    return i;
                });
            }

            public boolean removeMember(Dendrite d, Axon a) {
                return species.get(d).remove(a);
            }

            public Set<Integer> getResponse(int x, int y, int label) {
                Set<Integer> result;
                Set<Axon> a = species.get(new Dendrite(x, y, label));
                if (a != null) result = a.stream().map(axon -> axon.action).collect(Collectors.toSet());
                else result = new HashSet<>();
                return result;
            }

            public int numberOfMembers() {
                int result = 0;
                for(Set<Axon> s : species.values()) result += s.size();
                return result;
            }

            private HashMap<Dendrite, Set<Axon>> species = new HashMap<>();
        }

        public Population(Population o) {
            this.labeler = new Classifier(o.labeler);
            this.gen = o.gen;
            this.fitnessFunction = o.fitnessFunction.compose((Species s) -> s);
            this.currentSpecies = o.currentSpecies;
            this.population = new TreeMap<>((o1, o2) -> -(o1.compareTo(o2)));

            for(Map.Entry<Double, Set<Species>> e : o.population.entrySet())
            {
                for(Species s : e.getValue())
                {
                    this.addSpecies(e.getKey(), s);
                }
            }
        }

        public Population(Function<Species, Double> fitnessFunction, Classifier labeler, Species... species) {
            this.population = new TreeMap<>((o1, o2) -> -(o1.compareTo(o2)));
            this.fitnessFunction = fitnessFunction;
            this.labeler = labeler;
            Set<Species> specs = new HashSet<>();
            for (int i = 0; i < species.length; ++i) specs.add(species[i]);
            if(!specs.isEmpty()) this.population.put(0.0, specs);
        }

        public Species newSpecies() {
            return new Species();
        }
        Species randomGenSpecies(int m) {
            return randomGenSpecies(1, m).iterator().next();
        }
        public Set<Species> randomGenSpecies(int n, int m) {
            BiFunction<Integer, Integer, Integer> rngB = (lower, upper) ->
                    (new Random().nextInt(abs(upper - lower))
                            + new Random().nextInt(abs(upper - lower))
                            + new Random().nextInt(abs(upper - lower))
                            + (3 * lower)) / 3;
            Function<Integer, Integer> rng = (upper) -> rngB.apply(0, upper);

            Set<Species> result = new HashSet<>();

            int actions[] = new int[]{Mario.KEY_JUMP, Mario.KEY_RIGHT, Mario.KEY_SPEED};

            for (int i = 0; i < n; ++i)
            {
                Species s = newSpecies();
                for(int j = 0; j < m; ++j)
                {
                    s.addMember(rng.apply(Environment.HalfObsWidth*2), rng.apply(Environment.HalfObsHeight*2), new Random().nextInt(labeler.numberOfLabels()) + 1, actions[new Random().nextInt(3)]);
                }
                addSpecies(0.0, s);
                result.add(s);
            }
            return result;
        }
        public int nextGen() {
            double bestPercentile = 0.2;
            double worstPercentile = 0.2;
            double stochasticPopulationGrowth = 0.2;
            ArrayList<Species> best = removeBest(bestPercentile);
            removeWorst(worstPercentile);
            currentSpecies = 0;

            for (Species s : best) {
                addSpecies(new Species(s));
                if (new Random().nextDouble() < stochasticPopulationGrowth) addSpecies(new Species(s));
            }
            for (Set<Species> specs : population.values())
            {
                for(Species s : specs) s.mutate(2 * Environment.HalfObsWidth, 2 * Environment.HalfObsHeight, labeler.numberOfLabels(), Environment.numberOfButtons, 0.15);
            }

            return ++gen;
        }
        public Species nextSpecies() {
            if(currentSpecies >= size()) nextGen();
            int n = 0;
            Set<Species> speciesSet = null;
            for(Set<Species> specs : population.values())
            {
                if(n + specs.size() > currentSpecies) speciesSet = specs;
                n += specs.size();
            }
            Species specs[] = ((Species[]) speciesSet.toArray());
            return specs[currentSpecies - n];
        }


        public void setFitnessFunction(Function<Species, Double> fitnessFunction) {
            this.fitnessFunction = fitnessFunction;
        }

        public final Function<Species, Double> getFitnessFunction() {
            return fitnessFunction;
        }

        public final Double calculateFitness(Species species) {
            return fitnessFunction.apply(species);
        }

        public void updateFitness() {
            Set<Species> species = new HashSet<>(population.size());
            population.values().forEach(species::addAll);
            species.forEach(this::addSpecies);
        }

        public int updateFitness(double newFitness, Species species) {
            //if we can't find the entry, then inform the caller
            Double oldFitness = getFitness(species);
            if (oldFitness.isNaN()) return -1;
            return updateFitness(newFitness, species, oldFitness);
        }

        public int updateFitness(double newFitness, Species species, double oldFitness) {
            //remove old entry
            //if we didn't find it, then inform the caller
            if (!removeSpecies(oldFitness, species)) return -1;
            //place updated entry
            addSpecies(newFitness, species);
            //return ranking of entry
            return population.headMap(newFitness).size();
        }

        public int addSpecies(Species species) {
            return addSpecies(calculateFitness(species), species);
        }

        public int addSpecies(double fitness, Species species) {
            Set<Species> sl = new HashSet<>();
            sl.add(species);
            population.merge(fitness, sl, (a, b) -> {
                a.addAll(b);
                return a;
            });
            return population.tailMap(fitness).size();
        }

        public Double removeSpecies(Species species) {
            Double fitness = getFitness(species);
            if (!fitness.isNaN()) removeSpecies(fitness, species);
            return fitness;
        }

        public boolean removeSpecies(double fitness, Species species) {
            return population.get(fitness).remove(species);
        }

        public Species removeBest() {
            ArrayList<Species> best = removeBest(1);
            return (best.size() == 0 ? null : best.get(0));
        }

        public ArrayList<Species> removeBest(int n) {
            ArrayList<Species> result = new ArrayList<>(n);
            final int[] i = {0};
            while (i[0] < n && population.size() > 0) {
                Set<Species> set = population.get(population.firstKey());
                set.forEach(s -> {
                    if (i[0]++ < n) result.add(s);
                });
                result.forEach(set::remove);
                if (set.isEmpty()) population.remove(population.firstKey());
            }
            return result;
        }

        public ArrayList<Species> removeBest(double percentile) {
            return removeBest((int) (((double) population.size()) * percentile));
        }

        public Species removeWorst() {
            ArrayList<Species> worst = removeWorst(1);
            return (worst.size() == 0 ? null : worst.get(0));
        }

        public ArrayList<Species> removeWorst(int n) {
            ArrayList<Species> result = new ArrayList<>(n);
            final int[] i = {0};
            while (i[0] < n && population.size() > 0) {
                Set<Species> set = population.get(population.lastKey());
                set.forEach(s -> {
                    if (i[0]++ < n) result.add(s);
                });
                result.forEach(set::remove);
                if (set.isEmpty()) population.remove(population.lastKey());
            }
            return result;
        }

        public ArrayList<Species> removeWorst(double percentile) {
            return removeWorst((int) ceil(((double) population.size()) * percentile));
        }

        public Double replaceSpecies(Species old, Species replacement) {
            Double fitness = getFitness(old);
            replaceSpecies(fitness, old, replacement);
            return fitness;
        }

        public boolean replaceSpecies(double fitness, Species old, Species replacement) {
            Set<Species> set = population.get(fitness);
            set.add(replacement);
            return set.remove(old);
        }

        public final Double getFitness(Species species) {
            double fitness = Double.NaN;
            for (Map.Entry<Double, Set<Species>> e : population.entrySet()) {
                if (e.getValue().contains(species)) {
                    fitness = e.getKey();
                    break;
                }
            }
            return fitness;
        }

        public final Map.Entry<Double, Species> atRank(int n) {
            int i = 0;
            for (Map.Entry<Double, Set<Species>> specs : population.entrySet())
            {
                for(Species s : specs.getValue())
                {
                    if(++i == n) return new AbstractMap.SimpleEntry<>(specs.getKey(), s);
                }
            }
            return null;
        }

        public final SortedMap<Double, Set<Species>> atLeastRank(int n) {
            Map.Entry<Double, Species> e = atRank(n);
            if (e == null) return null;
            return population.headMap(e.getKey());
        }

        public final SortedMap<Double, Set<Species>> atMostRank(int n) {
            Map.Entry<Double, Species> e = atRank(n);
            if (e == null) return null;
            return population.tailMap(e.getKey());
        }

        public int getRank(Species species) {
            Double fitness = getFitness(species);
            if (fitness.isNaN()) return 0;
            return getRank(fitness, species);
        }

        public int getRank(double fitness, Species species) {
            if (!population.containsKey(fitness)) return 0;
            int i = 0;
            for (Map.Entry<Double, Set<Species>> e : population.entrySet()) {
                for (Species s : e.getValue()) {
                    ++i;
                    if (species.equals(s)) return i;
                }
            }
            return 0;
        }

        public final SortedMap<Double, Set<Species>> inTopPercentile(double percentile) {
            return atLeastRank((int) (((double) population.size()) * percentile));
        }

        public final SortedMap<Double, Set<Species>> inBottomPercentile(double percentile) {
            return atMostRank((int) (((double) population.size()) * (1.0 - percentile)));
        }

        public double getTopPercentile(Species species) {
            return getTopPercentile(getFitness(species), species);
        }

        public double getTopPercentile(double fitness, Species species) {
            int rank = getRank(fitness, species);
            if (rank == 0) return 0.0;
            return ((double) rank / (double) population.size());
        }

        public double getBottomPercentile(Species species) {
            return getTopPercentile(getFitness(species), species);
        }

        public double getBottomPercentile(double fitness, Species species) {
            int rank = getRank(fitness, species) - 1;
            if (rank == -1) return 0.0;
            return 1.0 - ((double) rank / (double) population.size());
        }

        public Map<Double, Set<Species>> getPopulation() {
            return population;
        }

        public int size() {
            int result = 0;
            for(Set<Species> specs : population.values()) result += specs.size();
            return result;
        }

        public int generation() {
            return gen;
        }

        public Classifier labeler;
        private int gen = 0;

        private Function<Species, Double> fitnessFunction;
        private TreeMap<Double, Set<Species>> population;
        private int currentSpecies = 0;
    }

    public class Classifier {
        Classifier(Classifier o) {
            this.classificationFilter = new ArrayList<>();
            for(Predicate<Byte> classification : o.classificationFilter) this.addClassification(classification.negate().negate());
        }
        Classifier(Predicate<Byte>... classifications) {
            this.classificationFilter = new ArrayList<>();
            this.classificationFilter.add(data -> data != 0);
            for(Predicate<Byte> classification : classifications) this.addClassification(classification);
        }

        public int addClassification(Predicate<Byte> qualifier) {
            classificationFilter.add(qualifier);
            return classificationFilter.size();
        }

        public Predicate<Byte> getClassification(int i) {
            return classificationFilter.get(i);
        }

        public int classify(Byte data) {
            for (int i = classificationFilter.size() - 1; i >= 0; --i) {
                if (classificationFilter.get(i).test(data)) return i + 1;
            }
            return 0;
        }

        public int numberOfLabels() {
            return classificationFilter.size();
        }

        private ArrayList<Predicate<Byte>> classificationFilter;
    }

//    public NEATAgent() {
//        this("GoOD ENuFF Agent");
//    }
    public NEATAgent(EvaluationOptions eval) {
        this.setName("GoOD ENuFF Agent");
        this.evaluator = new Evaluator(eval);
        //System.out.println(evaluator.evaluationInfo.agentName);
        this.population = new Population( species -> fitness(evaluator.evaluationInfo), new Classifier() );
        this.action = new boolean[Environment.numberOfButtons];
        this.population.randomGenSpecies(10, 20);
        this.fitest = 0.0;
        this.reset();
    }
    public NEATAgent(NEATAgent o) {
        this.setName(o.name);
        this.action = new boolean[o.action.length];
        for(int i = 0; i < o.action.length; ++i) action[i] = o.action[i];
        this.population = new Population(o.population);
        this.reset();
    }

    @Override
    public Evolvable getNewInstance() {
        return null;
    }

    @Override
    public Evolvable copy() {
        return null;
    }

    @Override
    public void mutate() {
        population.nextGen();
    }



    public void reset() {
        action = new boolean[Environment.numberOfButtons];
    }

    public boolean[] getAction(Environment observation) {
        Population.Species currentSpecies = population.atRank(1).getValue();
        fitest = new Random().nextDouble();
//        currentSpecies.print();
        reset();
        //Terrain Info Generalization level 1, Enemy Info Generalization level 0
        byte data[][] = observation.getMergedObservationZ(1, 0);
        for(int y = 0; y < data.length; ++y)
        {
            for(int x = 0; x < data[y].length; ++x)
            {
                Set<Integer> responses = currentSpecies.getResponse(x, y, population.labeler.classify(data[y][x]));

//                System.out.printf("%d ", data[y][x]);
//                System.out.printf("(%d, %d):\t%d\t", x, y, population.labeler.classify(data[y][x]));

                if(responses != null) {
                    for (Integer r : responses) {
                        action[r] = true;
                    }
                }
            }
//            System.out.println();
        }
//        System.out.println();
//        System.out.println();


        //action[Mario.KEY_RIGHT] = true;
        //action[Mario.KEY_SPEED] = true;
        action[Mario.KEY_JUMP] = action[Mario.KEY_JUMP] && observation.mayMarioJump() || !observation.isMarioOnGround();
//        System.out.println();
//        System.out.println();
        return action;
    }

    public AGENT_TYPE getType()
    {
        return Agent.AGENT_TYPE.AI;
    }

    public String getName() {
        return name;
    }

    public double getBestFitness() { return population.atRank(1).getKey();}

    public void setName(String Name) {
        this.name = Name;
    }

    public void setEvaluator(Evaluator evaluator) { this.evaluator = evaluator; }

    public double fitness(EvaluationInfo marioInfo) {
        return (marioInfo.marioStatus == 0) ? marioInfo.lengthOfLevelPassedPhys - abs(marioInfo.timeLeft-marioInfo.timeSpentOnLevel) : marioInfo.totalLengthOfLevelPhys - (marioInfo.timeSpentOnLevel-marioInfo.timeLeft);
    }
}
