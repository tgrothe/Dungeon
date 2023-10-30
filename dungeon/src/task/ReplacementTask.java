package task;

import reporting.GradingFunctions;

import java.util.ArrayList;
import java.util.List;

/**
 * A Replacement Task.
 *
 * <p>Stores a collection of elements as a solution and a set of rules as replacement terms.
 */
public class ReplacementTask extends Task {
    private final List<Element> solution;
    private final List<Rule> rules;

    /**
     * Create a new Replacement Task.
     *
     * @param rules Rules that define which and how elements can be replaced.
     */
    public ReplacementTask(List<Rule> rules) {
        super();
        this.solution = new ArrayList<>();
        this.rules = rules;
        scoringFunction(GradingFunctions.replacementGrading());
    }

    public void addSolution(Element e) {
        solution.add(e);
    }

    public List<Element> solution() {
        return new ArrayList<>(solution);
    }

    public List<Rule> rules() {
        return new ArrayList<>(rules);
    }

    /**
     * Rules for how elements can be replaced by each other.
     *
     * <p>This is basically an abstract implementation of {@link contrib.crafting.Recipe}.
     */
    public record Rule(boolean ordered, Element[] input, Element[] output) {}
}
