package reporting;

import task.*;
import task.quizquestion.MultipleChoice;
import task.quizquestion.SingleChoice;

import java.util.*;
import java.util.function.BiFunction;

/** Contanins different grading functions for the different task types. */
public class GradingFunctions {

    /**
     * A simple grading function for {@link SingleChoice} Task.
     *
     * <p>The function will check if the given answer is the correct answer, and if so, it will
     * return the full amount of points; otherwise, it will return 0 points.
     *
     * @return BiFunction that can be used for {@link Task#scoringFunction(BiFunction)}
     */
    public static BiFunction<Task, Set<TaskContent>, Float> singleChoiceGrading() {
        return (task, answer) -> {
            if (answer.size() != 1) return 0f;
            SingleChoice singleChoice = (SingleChoice) task;
            int index = singleChoice.correctAnswerIndices().get(0);
            TaskContent correctAnswer = singleChoice.contentByIndex(index);
            TaskContent givenAnswer =
                    answer.stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("No answer was given"));
            if (correctAnswer == givenAnswer) return singleChoice.points();
            else return 0f;
        };
    }

    /**
     * A simple grading function for the {@link MultipleChoice} Task.
     *
     * <p>For each correct answer, points will be added; for each wrong answer, points will be
     * removed.
     *
     * <p>The amount of points given/removed per answer is calculated by the number of points the
     * Task is worth divided by the count of correct answers.
     *
     * <p>The return value cannot be lower than 0.
     *
     * @return a BiFunction that can be used for {@link Task#scoringFunction(BiFunction)}
     */
    public static BiFunction<Task, Set<TaskContent>, Float> multipeChoiceGrading() {
        return (task, givenAnswers) -> {
            MultipleChoice multipleChoice = (MultipleChoice) task;

            Set<TaskContent> correctAnswers = new HashSet<>();
            for (int index : multipleChoice.correctAnswerIndices())
                correctAnswers.add(multipleChoice.contentByIndex(index));

            float pointsPerAnswer = multipleChoice.points() / correctAnswers.size();
            float reachedPoints = 0f;
            for (TaskContent answer : givenAnswers) {
                if (correctAnswers.contains(answer)) reachedPoints += pointsPerAnswer;
                else reachedPoints -= pointsPerAnswer;
            }
            return Math.max(0, reachedPoints);
        };
    }

    /**
     * A simple grading function for {@link ReplacementTask}.
     *
     * <p>Will give points for each correct answer.
     *
     * <p>The amount of points given per answer is calculated by the number of points the Task is
     * worth divided by the count of correct answers.
     *
     * @return a BiFunction that can be used for {@link Task#scoringFunction(BiFunction)}
     */
    public static BiFunction<Task, Set<TaskContent>, Float> replacementGrading() {
        return (task, answers) -> {
            ReplacementTask replacementTask = (ReplacementTask) task;

            List<Element> solution = replacementTask.solution();
            float pointsPerAnswer = replacementTask.points() / solution.size();
            float reachedPoints = 0f;
            for (TaskContent answer : answers)
                if (solution.contains(answer)) reachedPoints += pointsPerAnswer;
            return reachedPoints;
        };
    }

    /**
     * A simple grading function for the {@link AssignTask}.
     *
     * <p>For each correct answer, points will be added
     *
     * <p>The amount of points given per answer is calculated by the number of points the Task is
     * worth divided by the count of correct answers.
     *
     * @return a BiFunction that can be used for {@link Task#scoringFunction(BiFunction)}
     */
    public static BiFunction<Task, Set<TaskContent>, Float> assignGradingEasy() {
        return (task, containers) -> {
            AssignTask assignTask = (AssignTask) task;
            Map<Element, Set<Element>> solution = assignTask.solution();
            int elementCount = solution.values().stream().mapToInt(Set::size).sum();
            float pointsPerAnswer = assignTask.points() / elementCount;
            float reachedPoints = 0f;

            Element wrap = (Element) containers.stream().findFirst().orElseThrow();
            Map<Element, Set<Element>> givenAnswers = (Map<Element, Set<Element>>) wrap.content();

            for (Element container : givenAnswers.keySet()) {
                Set<Element> correctSolSet = solution.get(container);
                for (Element content : givenAnswers.get(container))
                    if (correctSolSet.contains(content)) reachedPoints += pointsPerAnswer;
            }
            return reachedPoints;
        };
    }

    /**
     * A simple grading function for the {@link AssignTask}.
     *
     * <p>For each correct answer, points will be added; for each wrong answer, points will be
     * removed.
     *
     * <p>The amount of points given/removed per answer is calculated by the number of points the
     * Task is worth divided by the count of correct answers.
     *
     * <p>The return value cannot be lower than 0.
     *
     * @return a BiFunction that can be used for {@link Task#scoringFunction(BiFunction)}
     */
    public static BiFunction<Task, Set<TaskContent>, Float> assignGradingHard() {
        return (task, containers) -> {
            AssignTask assignTask = (AssignTask) task;
            Map<Element, Set<Element>> solution = assignTask.solution();
            int elementCount = solution.values().stream().mapToInt(Set::size).sum();
            float pointsPerAnswer = assignTask.points() / elementCount;
            System.out.println(pointsPerAnswer);
            float reachedPoints = 0f;

            Element wrap = (Element) containers.stream().findFirst().orElseThrow();
            Map<Element, Set<Element>> givenAnswers = (Map<Element, Set<Element>>) wrap.content();

            for (Element container : givenAnswers.keySet()) {
                Set<Element> correctSolSet = solution.get(container);
                for (Element content : givenAnswers.get(container))
                    if (correctSolSet.contains(content)) reachedPoints += pointsPerAnswer;
                    else reachedPoints -= pointsPerAnswer;
            }
            return Math.max(0, reachedPoints);
        };
    }
}
