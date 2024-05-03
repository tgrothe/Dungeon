package dojo.rooms.rooms.riddle;

import contrib.components.InteractionComponent;
import contrib.hud.dialogs.OkDialog;
import contrib.level.generator.graphBased.RoomGenerator;
import core.Entity;
import core.components.DrawComponent;
import core.components.PositionComponent;
import core.level.utils.DesignLabel;
import core.level.utils.LevelSize;
import core.utils.components.path.SimpleIPath;
import dojo.rooms.LevelRoom;
import dojo.rooms.Room;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import task.Task;
import task.TaskContent;
import task.game.components.TaskComponent;
import task.game.hud.UIAnswerCallback;
import task.tasktype.Quiz;
import task.tasktype.quizquestion.FreeText;

/**
 * Informationen für den Spieler über diesen Raum:
 *
 * <p>In diesem Raum müssen verschiedene Design Patterns anhand eines UML-Klassendiagramms erkannt
 * werden. Die erkannten Design Patterns müssen dann dem Zauberer mitgeteilt werden.
 */
public class Fragen_Pattern extends Room {
  private final String[] expectedPatterns = {"observer", "visitor"};
  private final List<Integer> expectedPatternsIndices;

  {
    expectedPatternsIndices =
        IntStream.range(0, expectedPatterns.length).boxed().collect(Collectors.toList());
    Collections.shuffle(expectedPatternsIndices);
  }

  private int currentPatternIndex = 0;
  private int correctAnswerCount = 0;
  private Entity zauberer;

  /**
   * Generate a new room.
   *
   * @param levelRoom the level node
   * @param gen the room generator
   * @param nextRoom the rooms next room
   * @param levelSize the size of this room
   * @param designLabel the design label of this room
   */
  public Fragen_Pattern(
      LevelRoom levelRoom,
      RoomGenerator gen,
      Room nextRoom,
      LevelSize levelSize,
      DesignLabel designLabel) {
    super(levelRoom, gen, nextRoom, levelSize, designLabel);

    try {
      generate();
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to generate: " + getClass().getName() + ": " + e.getMessage(), e);
    }
  }

  private void generate() throws IOException {
    // add entities to room
    Set<Entity> roomEntities = new HashSet<>();

    // add boss
    roomEntities.add(questBoss());

    addRoomEntities(roomEntities);
  }

  private Entity questBoss() throws IOException {
    // add boss
    zauberer = new Entity("Zauberer von Patternson");
    zauberer.add(new PositionComponent());
    zauberer.add(new DrawComponent(new SimpleIPath("character/monster/orc_shaman")));

    setNextTask();

    return zauberer;
  }

  private void setNextTask() {
    final Quiz question = newFreeText();

    zauberer.add(new TaskComponent(question, zauberer));
    zauberer.add(
        new InteractionComponent(
            1,
            true,
            (e, who) ->
                UIAnswerCallback.askOnInteraction(question, showAnswersOnHud()).accept(e, who)));
  }

  private BiConsumer<Task, Set<TaskContent>> showAnswersOnHud() {
    return (task, taskContents) -> {
      String rawAnswer =
          taskContents.stream().map(t -> (Quiz.Content) t).findFirst().orElseThrow().content();
      String answer = rawAnswer.trim().toLowerCase(Locale.ROOT);

      if (answer.contains(expectedPatterns[expectedPatternsIndices.get(currentPatternIndex)])) {
        OkDialog.showOkDialog("Ihre Antwort ist korrekt!", "Ergebnis", () -> {});
        correctAnswerCount++;
        if (correctAnswerCount == expectedPatterns.length) {
          openDoors();
        }

        currentPatternIndex = (currentPatternIndex + 1) % expectedPatterns.length;
        setNextTask();
      } else {
        OkDialog.showOkDialog("Ihre Antwort ist nicht korrekt!", "Ergebnis", () -> {});
      }
    };
  }

  private Quiz newFreeText() {
    String fileNamePrefix = "dojo-dungeon/todo-assets/Fragen_Pattern/UML_Klassendiagramm";
    String questionText =
        "Welches Design-Pattern wird in dem UML-Klassendiagramm unter \""
            + fileNamePrefix
            + (expectedPatternsIndices.get(currentPatternIndex) + 1)
            + ".png\" dargestellt? Es reicht das Wort ohne den Zusatz Pattern!";
    return new FreeText(questionText);
  }
}
