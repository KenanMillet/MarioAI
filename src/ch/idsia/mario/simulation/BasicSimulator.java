package ch.idsia.mario.simulation;

import ch.idsia.ai.agents.Agent;
import ch.idsia.mario.engine.GlobalOptions;
import ch.idsia.mario.engine.MarioComponent;
import ch.idsia.mario.engine.sprites.Mario;
import ch.idsia.tools.EvaluationInfo;

/**
 * Created by IntelliJ IDEA.
 * User: Sergey Karakovskiy
 * Date: Apr 7, 2009
 * Time: 2:27:48 PM
 * Package: .Simulation
 */

public class BasicSimulator implements Simulation
{
    SimulationOptions simulationOptions = null;
    private MarioComponent marioComponent;

    public BasicSimulator(SimulationOptions simulationOptions)
    {
        GlobalOptions.VisualizationOn = simulationOptions.isVisualization();
        System.out.println("this: " + GlobalOptions.getMarioComponent().getName());
        this.marioComponent = GlobalOptions.getMarioComponent();
        //System.out.println(GlobalOptions.getMarioComponent().getName());
        System.out.println(marioComponent.getName());
        this.setSimulationOptions(simulationOptions);
    }

    private MarioComponent prepareMarioComponent()
    {
        Agent agent = simulationOptions.getAgent();
        agent.reset();
        System.out.println(marioComponent.getName());
        if (GlobalOptions.getMarioComponent() == null) { System.out.println("help"); }
        System.out.println(agent.getName());
        System.out.println(agent.getType());
        marioComponent.setAgent(agent);
        System.out.println(marioComponent.getName());
        return marioComponent;
    }

    public void setSimulationOptions(SimulationOptions simulationOptions)
    {
        this.simulationOptions = simulationOptions;
    }

    public EvaluationInfo simulateOneLevel()
    {
        Mario.resetStatic(simulationOptions.getMarioMode());        
        prepareMarioComponent();
        marioComponent.setZLevelScene(simulationOptions.getZLevelMap());
        marioComponent.setZLevelEnemies(simulationOptions.getZLevelEnemies());
        marioComponent.startLevel(simulationOptions.getLevelRandSeed(), simulationOptions.getLevelDifficulty()
                                 , simulationOptions.getLevelType(), simulationOptions.getLevelLength(),
                                  simulationOptions.getTimeLimit());
        marioComponent.setPaused(simulationOptions.isPauseWorld());
        marioComponent.setZLevelEnemies(simulationOptions.getZLevelEnemies());
        marioComponent.setZLevelScene(simulationOptions.getZLevelMap());
        marioComponent.setMarioInvulnerable(simulationOptions.isMarioInvulnerable());
        return marioComponent.run1(simulationOptions.currentTrial++,
                simulationOptions.getNumberOfTrials()
        );
    }
}
