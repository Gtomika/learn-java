package com.gaspar.learnjava.curriculum;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;

import com.gaspar.learnjava.ChapterActivity;
import com.gaspar.learnjava.CoursesActivity;
import com.gaspar.learnjava.LearnJavaActivity;
import com.gaspar.learnjava.UpdatableActivity;
import com.gaspar.learnjava.asynctask.ChapterStatusDisplayerTask;
import com.gaspar.learnjava.database.ChapterStatus;
import com.gaspar.learnjava.database.ExamStatus;
import com.gaspar.learnjava.database.LearnJavaDatabase;
import com.gaspar.learnjava.parsers.CourseParser;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * <p>
 *   Represents a chapter in the curriculum. A Chapter is a sub component of a course,
 *   and it consists of any number of text, code or advanced information components.
 * </p>
 *
 * <p>
 *   Chapters are stored in XML, in the following format: chapter_id.xml
 * </p>
 *
 * {@code
 * <resources>
 *  *     <chapterdata>
 *  *         <id>*id here*</id>
 *  *         <name>*chapter name here*</name>
 *  *     </chapterdata>
 *  *     <text>*text component*</text>
 *  *     <code>*code component*</code>
 *  *     <advanced>*advanced information component*</advanced>
 *  </resources>
 * }
 */
public class Chapter implements Serializable {

    public static final String CHAPTER_PREFERENCE_STRING = "passed_chapter";

    /**
     * The id of the chapter.
     */
    private int id;

    /**
     * The name if the chapter.
     */
    private String name;

    /**
     * Displayable components of the chapter.
     */
    private transient List<Component> components;

    /**
     * Status of this chapter. This can only be unlocked or completed, but the user will not be able
     * to access chapters in locked courses anyways.
     */
    @Status
    private volatile int status;

    public Chapter(int id, String name, List<Component> components) {
        this.id = id;
        this.name = name;
        this.components = components;
        status = Status.NOT_QUERIED;
    }

    /**
     * Creates a chapter object that has no components. This can be used when only the name and ID is
     * important.
     */
    public Chapter(int id, String name) {
        this.id = id;
        this.name = name;
        status = Status.NOT_QUERIED;
    }

    /**
     * Finds the status of tis course in the database, saves it in
     * the status variable, and displays it with the given image view.
     * Uses a background thread.
     */
    public void queryAndDisplayStatus(final ImageView imageView, Context context) {
        new ChapterStatusDisplayerTask(this).execute(imageView, context);
    }

    /**
     * Starts a chapter activity for result for result. It will pass the chapter and the status icon
     * of this chapter.
     *
     * @param updateView The view that will be updated when the started activity finishes.
     */
    public static void startChapterActivity(AppCompatActivity fromActivity, Chapter chapter, View updateView) {
        SharedPreferences prefs = fromActivity.
                getSharedPreferences(LearnJavaActivity.APP_PREFERENCES_NAME, Context.MODE_PRIVATE); //save started chapter
        prefs.edit().putInt(LearnJavaActivity.ACTIVE_CHAPTER_ID_PREFERENCE, chapter.id).apply();
        Intent intent = new Intent(fromActivity, ChapterActivity.class);
        intent.putExtra(CHAPTER_PREFERENCE_STRING, chapter);
        if(fromActivity instanceof UpdatableActivity) {
            ((UpdatableActivity)fromActivity).setUpdateView(updateView); //save update view
        }
        fromActivity.startActivityForResult(intent, CoursesActivity.CHAPTER_REQUEST_CODE); //start with chapters code
    }

    /**
     * Updates a chapter in the database to have the {@link Status#COMPLETED} status. After updating
     * it checks if all the chapters in the course have been confirmed, and if so it unlocks the exam of the course.
     */
    @UiThread
    public void markChapterAsCompleted(Context context) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ChapterStatus newStatus = new ChapterStatus(id,
                    com.gaspar.learnjava.curriculum.Status.COMPLETED);
            LearnJavaDatabase.getInstance(context).getChapterDao().updateChapterStatus(newStatus);

            if(CoursesActivity.coursesNotParsed()) { //check if courses are parsed
                try {
                    CoursesActivity.getParsedCourses().addAll(CourseParser.getInstance().parseCourses(context));
                } catch (Exception e) {
                    Log.e("LearnJava", "Exception", e);
                }
            }
            Course courseOfChapter = null; //find course of chapter
            outerLoop:
            for(Course course: CoursesActivity.getParsedCourses()) {
                for(Chapter chapter: course.getChapters()) {
                    if(chapter.getId() == id) {
                        courseOfChapter = course;
                        break outerLoop;
                    }
                }
            }
            if(courseOfChapter == null) throw new RuntimeException("Resource integrity error!");
            ExamStatus eStatus = LearnJavaDatabase.getInstance(context).getExamDao()
                    .queryExamStatus(courseOfChapter.getExam().getId());
            if(eStatus.getStatus() != Status.LOCKED) return; //if already completed/unlocked the no need to check
            boolean allConfirmed = true;
            List<Integer> statuses = LearnJavaDatabase.getInstance(context).getChapterDao().getAllChapterStatuses();
            for(@Status int status: statuses) {
                if(status != Status.COMPLETED) { //found a chapter in the course that is not completed
                    allConfirmed = false;
                    break;
                }
            }
            if(allConfirmed) { //exam still locked, all chapters confirmed
                //unlock exam in database
                LearnJavaDatabase.getInstance(context).getExamDao().
                        updateExamCompletionStatus(courseOfChapter.getExam().getId(), Status.UNLOCKED);
            }
        });
    }

    /**
     * Checks if there is a chapter in the database with the given id. If not it adds this chapter
     * to the database with default status.
     */
    @WorkerThread
    public static void validateChapterStatus(int chapterId, Context context) {
        ChapterStatus status = LearnJavaDatabase.getInstance(context).getChapterDao().queryChapterStatus(chapterId);
        if(status == null) { //not found in the database
            @Status final int DEF_STATUS = Status.UNLOCKED;
            ChapterStatus newStatus = new ChapterStatus(chapterId, DEF_STATUS);
            LearnJavaDatabase.getInstance(context).getChapterDao().addChapterStatus(newStatus); //add to database
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Component> getComponents() {
        return components;
    }

    public @Status int getStatus() {
        return status;
    }

    public void setStatus(@Status int status) {
        this.status = status;
    }
}
