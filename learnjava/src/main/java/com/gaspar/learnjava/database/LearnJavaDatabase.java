package com.gaspar.learnjava.database;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.gaspar.learnjava.curriculum.Chapter;
import com.gaspar.learnjava.curriculum.Course;
import com.gaspar.learnjava.curriculum.Exam;
import com.gaspar.learnjava.curriculum.Task;
import com.gaspar.learnjava.parsers.CourseParser;
import com.gaspar.learnjava.parsers.ExamParser;
import com.gaspar.learnjava.parsers.TaskParser;
import com.gaspar.learnjava.utils.LocalizationUtils;
import com.gaspar.learnjava.utils.LogUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A singleton class representing the app database. All methods of this class should only
 * be used in background threads.
 */
@Database(entities = {CourseStatus.class, ChapterStatus.class, TaskStatus.class, ExamStatus.class, PlaygroundFile.class},
        version = 2, exportSchema = false)
@WorkerThread
public abstract class LearnJavaDatabase extends RoomDatabase {

    /**
     * The only database instance.
     */
    private static LearnJavaDatabase instance;

    /**
     * Retrieves the database instance. If it does not exist yet, then it will be created.
     * @param context Context.
     * @return The database instance.
     */
    public static LearnJavaDatabase getInstance(@NonNull Context context) {
        if(instance == null) {
            instance = Room.databaseBuilder(context, LearnJavaDatabase.class, "learn_java_database")
                    .addMigrations(MIGRATION_1_2)
                    .build();
        }
        return instance;
    }

    /**
     * @return An object which is used to modify the course table.
     */
    public abstract CourseDao getCourseDao();

    /**
     * @return An object which is used to modify the chapter table.
     */
    public abstract ChapterDao getChapterDao();

    /**
     * @return An object which is used to modify the task table.
     */
    public abstract TaskDao getTaskDao();

    /**
     * @return An object which is used to modify the exam table.
     */
    public abstract ExamDao getExamDao();

    /**
     * @return An object which is used to modify the playground files table.
     */
    public abstract PlaygroundFileDao getPlaygroundFileDao();

    /**
     * Goes through all asset files and checks if the curriculum elements (course, task, ...)
     * are added to the database or not. If not it adds them.
     */
    public static void validateDatabase(@NonNull Context context) {
        LogUtils.log("BEGINNING TO VALIDATE DATABASE!");
        List<Integer> idList = new ArrayList<>();
        final AssetManager manager = context.getAssets(); //get access to assets
        final String localizedAssets = LocalizationUtils.getLocalizedAssetPath();
        try {
            final XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            //list all courses
            final String[] coursePaths = manager.list(localizedAssets + "/courses"); //list course XML-s in the courses asset folder
            for(String relCoursePath: coursePaths) { //check every course XML for the correct id
                String coursePath = localizedAssets + "/courses/" + relCoursePath;
                try(final InputStream is = manager.open(coursePath)) { //open course XML as input stream
                    final XmlPullParser parser = factory.newPullParser();
                    parser.setInput(is, "UTF-8");
                    final Course course = CourseParser.getInstance().parseCourse(parser, context); //parse this course
                    idList.add(course.getId());
                }
            }
            /* important, as the one with the smallest id is the first course, and that must get unlocked by default. */
            Collections.sort(idList);
            for(int courseId: idList) {
                Course.validateCourseStatus(courseId, context);
            }
            //list and validate all chapters
            final String[] chapterPaths = manager.list(localizedAssets + "/chapters");
            for(String relChapterPath: chapterPaths) { //check every chapter XML for the correct id
                String chapterPath = localizedAssets + "/chapters/" + relChapterPath;
                try(final InputStream is = manager.open(chapterPath)) { //open chapter XML as input stream
                    final XmlPullParser parser = factory.newPullParser();
                    parser.setInput(is, "UTF-8");
                    final Chapter chapter = CourseParser.getInstance().parseChapterData(parser, false, context); //parse this chapter
                    Chapter.validateChapterStatus(chapter.getId(), context); //validate
                }
            }
            //list and validate tasks
            final String[] taskPaths = manager.list(localizedAssets + "/tasks");
            for(String relTaskPath: taskPaths) { //check every task XML for the correct id
                String taskPath = localizedAssets + "/tasks/" + relTaskPath;
                try(final InputStream is = manager.open(taskPath)) { //open task XML as input stream
                    final XmlPullParser parser = factory.newPullParser();
                    parser.setInput(is, "UTF-8");
                    final Task task = TaskParser.getInstance().parseTaskData(parser, false, context); //parse this chapter
                    Task.validateTaskStatus(task.getId(), context); //validate
                }
            }
            //list and validate exams
            final String[] examPaths = manager.list(localizedAssets + "/exams");
            for(String relExamPath: examPaths) { //check every exam XML for the correct id
                String examPath = localizedAssets + "/exams/" + relExamPath;
                try(final InputStream is = manager.open(examPath)) { //open exam XML as input stream
                    final XmlPullParser parser = factory.newPullParser();
                    parser.setInput(is, "UTF-8");
                    final Exam exam = ExamParser.getInstance().parseExamData(parser, false); //parse this exam
                    Exam.validateExamStatus(exam.getId(), context); //validate
                }
            }
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException("Failed to validate database: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        LogUtils.log("FINISHED VALIDATION OF DATABASE!");
    }

    /**
     * Deletes all records from the database.
     */
    public static void resetDatabase(@NonNull Context context) {
        LearnJavaDatabase database = LearnJavaDatabase.getInstance(context);
        database.getCourseDao().deleteRecords();
        database.getChapterDao().deleteRecords();
        database.getExamDao().deleteRecords();
        database.getTaskDao().deleteRecords();
        database.getPlaygroundFileDao().deleteRecords();
        CourseStatus.initCourseCount(0, context); //also reset course counter variable
    }

    /*
     * ----------------------------- MIGRATIONS ----------------------------------------------------
     */

    /**
     * Migration from database version 1 to version 2. In this migration, the new table represented by
     * {@link PlaygroundFile} was added.
     */
    private static final Migration MIGRATION_1_2 = new Migration(1,2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE `playground_files` (`file_name` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY (`file_name`))");
        }
    };

    /*
    private static final Migration MIGRATION_2_3 = new Migration(2,3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {

        }
    };

    private static final Migration MIGRATION_3_4 = new Migration(3,4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {

        }
    };
    */
}
