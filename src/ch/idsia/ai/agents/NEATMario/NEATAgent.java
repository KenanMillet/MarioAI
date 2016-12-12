package ch.idsia.ai.agents.NEATMario;

import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.mario.environments.Environment;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Math.ceil;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;


/**
 * Created by Owner on 12/8/2016.
 */
public class NEATAgent implements Agent {

    protected boolean action[] = new boolean[Environment.numberOfButtons];
    protected String name;
    protected Classifier labeler = new Classifier();
    protected Population population = new Population(
            species -> { return 1.0;
            }
    );
    protected Population.Species currentSpecies;


    public class Population {

        public class Species {
            public Species() {

            }

            public Species(Map.Entry<Dendrite, Axon>... members) {
                for (int i = 0; i < members.length; ++i)
                    addMember(members[i].getKey().loc.x, members[i].getKey().loc.y, members[i].getKey().label, members[i].getValue().action);
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

                public int action;
            }

            public void addMember(int x, int y, int label, int action) {
                Set<Axon> set = new HashSet<>();
                set.add(new Axon(action));
                Dendrite d = new Dendrite(x, y, label);
                species.merge(d, set, (a, b) -> {
                    a.addAll(b);
                    return a;
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

            private HashMap<Dendrite, Set<Axon>> species = new HashMap<>();
        }

        public Species make_Species() {
            return new Species();
        }

        Population(Function<Species, Double> fitnessFunction, Species... species) {
            this.fitnessFunction = fitnessFunction;
            Set<Species> specs = new HashSet<>();
            for (int i = 0; i < species.length; ++i) specs.add(species[i]);
            this.population.put(0.0, specs);
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

        public final Map.Entry<Double, Set<Species>> atRank(int n) {
            int i = 0;
            for (Iterator<Map.Entry<Double, Set<Species>>> itr = population.entrySet().iterator(); i < n && itr.hasNext(); ++i) {
                if (i == n - 1) return itr.next();
                itr.next();
            }
            return null;
        }

        public final SortedMap<Double, Set<Species>> atLeastRank(int n) {
            Map.Entry<Double, Set<Species>> e = atRank(n);
            if (e == null) return null;
            return population.headMap(e.getKey());
        }

        public final SortedMap<Double, Set<Species>> atMostRank(int n) {
            Map.Entry<Double, Set<Species>> e = atRank(n);
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
                    if (species == s) return i;
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

        private Function<Species, Double> fitnessFunction;
        private TreeMap<Double, Set<Species>> population = new TreeMap<>((o1, o2) -> -(o1.compareTo(o2)));
    }

    public class Classifier {
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

        private ArrayList<Predicate<Byte>> classificationFilter = new ArrayList<>();
    }

    public NEATAgent() {
        this("NEAT \"Good 'nuff\" Agent");
    }
    public NEATAgent(String s)
    {
        setName(s);
        currentSpecies = population.make_Species();
//        currentSpecies.addMember(0, 12, 1, Mario.KEY_JUMP);
//        currentSpecies.addMember(1, 12, 1, Mario.KEY_JUMP);
//        currentSpecies.addMember(2, 12, 1, Mario.KEY_JUMP);

        currentSpecies.addMember(9, 16, 1, Mario.KEY_RIGHT);
        currentSpecies.addMember(12, 12, 1, Mario.KEY_JUMP);
        currentSpecies.addMember(12, 12, 1, Mario.KEY_SPEED);
        population.addSpecies(currentSpecies);
        labeler.addClassification(aByte -> aByte == -10);
    }

    public void reset() {
        action = new boolean[Environment.numberOfButtons];
    }

    public boolean[] getAction(Environment observation) {
        reset();
        //Terrain Info Generalization level 1, Enemy Info Generalization level 0
        byte data[][] = observation.getMergedObservationZ(1, 0);
        for(int y = 0; y < data.length; ++y)
        {
            for(int x = 0; x < data[y].length; ++x)
            {
                Set<Integer> responses = currentSpecies.getResponse(x, y, labeler.classify(data[y][x]));

//                System.out.printf("%d ", data[y][x]);
                System.out.printf("(%d, %d):\t%d\t", x, y, labeler.classify(data[y][x]));

                if(responses != null) {
                    for (Integer r : responses) {
                        action[r] = true;
                    }
                }
            }
            System.out.println();
        }
        System.out.println();
        System.out.println();


        //action[Mario.KEY_RIGHT] = true;
        //action[Mario.KEY_SPEED] = true;
        action[Mario.KEY_JUMP] = action[Mario.KEY_JUMP] && observation.mayMarioJump() || !observation.isMarioOnGround();
        System.out.println();
        System.out.println();
        return action;
    }

    public AGENT_TYPE getType()
    {
        return Agent.AGENT_TYPE.AI;
    }

    public String getName() {
        return name;
    }

    public void setName(String Name) {
        this.name = Name;
    }
}
