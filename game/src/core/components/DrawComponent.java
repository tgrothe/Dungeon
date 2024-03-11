package core.components;

import core.Component;
import core.systems.VelocitySystem;
import core.utils.ResourceWalker;
import core.utils.components.draw.Animation;
import core.utils.components.draw.CoreAnimations;
import core.utils.components.path.IPath;
import core.utils.components.path.SimpleIPath;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Store all {@link Animation}s for an entity.
 *
 * <p>At creation, the component will read in each subdirectory in the given path and create an
 * animation for each subdirectory.
 *
 * <p>Each Animation will be created with default settings. If you want to change these settings,
 * use the methods from {@link Animation}.
 *
 * <p>The {@link core.systems.DrawSystem} uses a Priority-based queue. Use {@link
 * #queueAnimation(IPath...)} or {@link #queueAnimation(int, IPath...)} to add an animation to the
 * queue. The {@link core.systems.DrawSystem} will always show the animation with the highest
 * priority in the queue.
 *
 * <p>Use {@link #currentAnimation} to get the current active animation or use {@link #animation} to
 * get a specific animation.
 *
 * <p>Use {@link #hasAnimation} to check if the component has the desired animation.
 *
 * <p>If you want to add your own Animations, create a subdirectory for the animation and add the
 * path to an enum that implements the {@link IPath} interface. So if you want to add a jump
 * animation to the hero, just create a new directory "jump" in the asset directory of your hero
 * (for example character/hero) and then add a new Enum-Value JUMP("jump") to the enum that
 * implements {@link IPath}.
 *
 * <p>Animations will be searched in the default asset directory. Normally, this is "game/assets",
 * but you can change it in the gradle.build file if you like.
 *
 * <p>Note: Each entity needs at least a {@link CoreAnimations#IDLE} Animation.
 *
 * @see Animation
 * @see IPath
 */
public final class DrawComponent implements Component {
  private final Logger LOGGER = Logger.getLogger(this.getClass().getSimpleName());

  /** allows only one Element from a certain priority and orders them */
  private final Map<IPath, Integer> animationQueue =
      new TreeMap<>(Comparator.comparingInt(IPath::priority));

  private Map<String, Animation> animationMap = null;
  private Animation currentAnimation;

  /**
   * Create a new DrawComponent.
   *
   * <p>Will read in all subdirectories of the given path and use each file in the subdirectory to
   * create an animation. So each subdirectory should contain only the files for one animation.
   *
   * <p>Animations should not be set directly via {@link #currentAnimation()} but rather be queued
   * via {@link #queueAnimation(IPath...)} or {@link #queueAnimation(int, IPath...)}.
   *
   * <p>Will set the current animation to either idle down, idle left, idle right, idle up, or idle,
   * depending on which one of these animations exists.
   *
   * <p>If no animations for any idle-state exist, {@link Animation#defaultAnimation()} for "IDLE"
   * is set.
   *
   * @param path Path (as a string) to the directory in the assets folder where the subdirectories
   *     containing the animation files are stored. Example: "character/knight".
   * @throws IOException if the given path does not exist.
   * @see Animation
   */
  public DrawComponent(final IPath path) throws IOException {
    // fetch available animations
    try {
      loadAnimationAssets(path);
      currentAnimation(
          CoreAnimations.IDLE_DOWN,
          CoreAnimations.IDLE_LEFT,
          CoreAnimations.IDLE_RIGHT,
          CoreAnimations.IDLE_UP,
          CoreAnimations.IDLE);

      // if no idle animation exists, set the missing texture animation as idle
      if (currentAnimation == null) {
        animationMap.put(CoreAnimations.IDLE.pathString(), Animation.defaultAnimation());
        currentAnimation(CoreAnimations.IDLE);
      }
    } catch (NullPointerException np) {
      // We convert the "NullPointerException" to a "FileNotFoundException" because the only
      // reason for a NullPointerException is if the directory does not exist.
      throw new FileNotFoundException("Path " + path + " not found.");
    }
  }

  /**
   * Create a new DrawComponent with a specific animation.
   *
   * <p>The given animation will be used as the idle animation.
   *
   * <p>This constructor is for a special case only. Use {@link DrawComponent(String)} if possible.
   *
   * @param idle Animation to use as the idle animation.
   */
  public DrawComponent(final Animation idle) {
    animationMap = new HashMap<>();
    animationMap.put(CoreAnimations.IDLE_LEFT.pathString(), idle);
    animationMap.put(CoreAnimations.IDLE_RIGHT.pathString(), idle);
    currentAnimation = idle;
  }

  /**
   * Get the current animation being displayed on the entity.
   *
   * @return The current animation of the entity.
   */
  public Animation currentAnimation() {
    return currentAnimation;
  }

  /**
   * Set the current animation displayed on the entity.
   *
   * <p>If the animation passed is not displayed on the entity, there may be another point in the
   * code where the animation is overwritten on the same tick (e.g., in {@link VelocitySystem}).
   *
   * <p>If the given animation is not stored in this component, a warning is logged.
   *
   * @param animationName Path of the new current animation (this is the name of the directory). If
   *     more than one path is given, the first one that exists will be set as the new current
   *     animation.
   * @see IPath
   */
  public void currentAnimation(final IPath... animationName) {
    for (IPath animationPath : animationName) {
      Animation animation = animationMap.get(animationPath.pathString());
      if (animation != null) {
        currentAnimation = animation;
        return;
      } else
        LOGGER.warning(
            "Animation "
                + animationPath
                + " can not be set, because the given Animation could not be found.");
    }
  }

  /**
   * Queue up an Animation to be considered as the next played Animation.
   *
   * <p>Animations are given as an {@link IPath} Array or multiple variables. Animation length is
   * set to one frame. If you need to queue longer Animations, use {@link #queueAnimation(int,
   * IPath...)}. The First existing Animation * will be added to the queue.
   *
   * @param next Array of IPaths representing the Animation.
   */
  public void queueAnimation(final IPath... next) {
    queueAnimation(1, next);
  }

  /**
   * Queue up an Animation to be considered as the next played Animation.
   *
   * <p>Animations are given as an {@link IPath} Array or multiple variables. The First existing
   * Animation will be added to the queue. If the Animation is already added, the remaining Frames
   * are set to the highest of remaining or new.
   *
   * <p>Animation length is set to the given parameter.
   *
   * @param forFrames Number of frames to play the Animation for.
   * @param next Array of IPaths representing the Animation.
   */
  public void queueAnimation(int forFrames, final IPath... next) {
    for (IPath path : next) {
      // is an existing animation of the component
      if (animationMap.containsKey(path.pathString())) {
        // check if the path is already queued
        if (animationQueue.containsKey(path)) {
          // update time of the animation
          animationQueue.put(path, Math.max(animationQueue.get(path), forFrames));
        } else {
          // add animation
          animationQueue.put(path, forFrames);
        }
        return;
      }
    }
  }

  /**
   * Remove the given animation from the animation queue.
   *
   * <p>This method removes the animation specified by the provided path from the animation queue.
   *
   * @param animation The path of the animation to remove from the queue.
   */
  public void deQueue(final IPath animation) {
    animationQueue.remove(animation);
  }

  /**
   * Remove all animations with the given priority from the animation queue.
   *
   * <p>This method removes all animations from the animation queue that have the specified
   * priority.
   *
   * @param prio The priority of animations to remove.
   */
  public void deQueueByPriority(int prio) {
    animationQueue.keySet().removeIf(e -> e.priority() == prio);
  }

  /**
   * Get the Animation at the given path.
   *
   * <p>Can be null if the component does not store an animation with this path.
   *
   * @param path Path of the Animation.
   * @return The animation or null.
   */
  public Optional<Animation> animation(final IPath path) {
    return Optional.ofNullable(animationMap.get(path.pathString()));
  }

  /**
   * Check if the component stores an animation with the given path.
   *
   * @param path Path of the animation to look for.
   * @return true if the animation exists in this component, false if not.
   */
  public boolean hasAnimation(final IPath path) {
    return animationMap.containsKey(path.pathString());
  }

  /**
   * Check if the animation at the given path is the current animation.
   *
   * <p>Will log a warning if no animation is stored for the given path.
   *
   * @param path Path to the animation to check.
   * @return true if the current animation equals the animation at the given path, false if not, or
   *     no animation for the given path is stored in this component.
   */
  public boolean isCurrentAnimation(final IPath path) {
    Optional<Animation> animation = animation(path);
    if (animation.isPresent()) return animation.get() == currentAnimation;
    LOGGER.warning("Animation " + path + " is not stored.");
    return false;
  }

  /**
   * Check if the current animation is a looping animation.
   *
   * @return true if the current animation is looping.
   */
  public boolean isCurrentAnimationLooping() {
    return currentAnimation.isLooping();
  }

  /**
   * Check if the current animation has finished playing.
   *
   * @return true if the current animation has finished playing.
   */
  public boolean isCurrentAnimationFinished() {
    return currentAnimation.isFinished();
  }

  /**
   * Check if the Animation is queued up.
   *
   * @return true if the Animation is in the queue.
   */
  public boolean isAnimationQueued(final IPath requestedAnimation) {
    for (Map.Entry<IPath, Integer> animationArr : animationQueue.entrySet()) {
      if (animationArr.getKey().pathString().equals(requestedAnimation.pathString())) return true;
    }
    return false;
  }

  /**
   * Get a copy of the animation queue.
   *
   * @return The entire queue of animations.
   */
  public Map<IPath, Integer> animationQueue() {
    return new HashMap<>(animationQueue);
  }

  /**
   * Get a copy of the Map that contains all the Animations in this component.
   *
   * <p>This method returns a new HashMap containing a copy of the original animationMap. Modifying
   * the returned map will not affect the internal state of this component.
   *
   * @return A new Map containing a copy of the animationMap.
   */
  public Map<String, Animation> animationMap() {
    return new HashMap<>(animationMap);
  }

  /**
   * Replace the current animationMap with a new Map.
   *
   * <p>This method allows replacing the entire animationMap with a new one provided as a parameter.
   * The new animationMap is a mapping of animation names (String) to their corresponding Animation
   * objects.
   *
   * @param animationMap the new animationMap.
   */
  public void animationMap(final Map<String, Animation> animationMap) {
    if (animationMap == null) throw new IllegalArgumentException("AnimationMap can not be null");
    this.animationMap = new HashMap<>(animationMap);
  }

  // Helper method that loads the animation assets from the given path. Called by the constructor,
  // the animation map will be constructed from the subdirectories of the given path.
  private void loadAnimationAssets(final IPath path) throws IOException {
    try {
      // Walk through the (sub)directories of path and fill the subdirectoryMap.
      final Map<String, List<Path>> subdirectoryMap =
          ResourceWalker.walk(path, Files::isRegularFile);
      final Map<String, List<IPath>> subdirectoryMap2 = new HashMap<>();
      for (Map.Entry<String, List<Path>> entry : subdirectoryMap.entrySet()) {
        subdirectoryMap2.put(
            entry.getKey(),
            entry.getValue().stream()
                .map(p -> new SimpleIPath(p.toString()))
                .collect(Collectors.toList()));
      }

      // A Map with sorted values (IPath lists) in natural string order (ascending)
      animationMap =
          subdirectoryMap2.entrySet().stream()
              .collect(
                  Collectors.toMap(Map.Entry::getKey, DrawComponent::getAnimationFromMapEntry));
    } catch (Exception e) {
      e.printStackTrace();
      throw new IOException(e);
    }
  }

  /**
   * Returns an Animation with sorted paths from the given {@code Map.Entry<String, List<IPath>> x}.
   */
  private static Animation getAnimationFromMapEntry(Map.Entry<String, List<IPath>> x) {
    return Animation.fromCollection(
        x.getValue().stream().sorted(Comparator.comparing(IPath::pathString)).toList());
  }
}
