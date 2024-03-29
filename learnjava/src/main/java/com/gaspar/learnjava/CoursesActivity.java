package com.gaspar.learnjava;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.gaspar.learnjava.asynctask.FillCourseActivityTask;
import com.gaspar.learnjava.asynctask.ShowCongratulationTask;
import com.gaspar.learnjava.curriculum.Chapter;
import com.gaspar.learnjava.curriculum.Course;
import com.gaspar.learnjava.curriculum.Exam;
import com.gaspar.learnjava.curriculum.Status;
import com.gaspar.learnjava.curriculum.Task;
import com.gaspar.learnjava.database.CourseStatus;
import com.gaspar.learnjava.utils.AnimationUtils;
import com.gaspar.learnjava.utils.DrawerUtils;
import com.google.android.gms.ads.AdView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Activity that displays all courses. They can be opened to display the chapters.
 */
public class CoursesActivity extends ThemedActivity implements NavigationView.OnNavigationItemSelectedListener
            , UpdatableActivity {

    /**
     * Used by background threads to indicate if the activity has loaded successfully.
     */
    public volatile boolean successfulLoad;

    /**
     * A list of the course objects parsed by the app.
     */
    private static List<Course> PARSED_COURSES = new ArrayList<>();

    /**
     * View that displays ads in this activity.
     */
    private AdView adView;

    /**
     * X position of the draggable floating action button.
     */
    private float dX;

    /**
     * Y position of the draggable floating action button.
     */
    private float dY;

    /**
     * Stores if the drop down floating button should open (drop down) or close (drop up)
     * all unlocked course selectors. The initial value of this depends on the auto drop down
     * setting.
     */
    private boolean dropDown;

    /**
     * This object can launch a {@link ChapterActivity}.
     */
    private ActivityResultLauncher<Intent> chapterActivityLauncher;

    /**
     * This object can launch a {@link TaskActivity}.
     */
    private ActivityResultLauncher<Intent> taskActivityLauncher;

    /**
     * This object can launch an {@link ExamActivity}.
     */
    private ActivityResultLauncher<Intent> examActivityLauncher;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        setContentView(R.layout.activity_courses);
        //true if auto slide open is DISABLED, false otherwise
        dropDown = !SettingsActivity.autoSlideOpenEnabled(this);
        setUpUI();
        //define what should happen when a chapter activity is finished
        chapterActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        if(updateViews.length == 0) return; //should not happen
                        ImageView icon = updateViews[0].findViewById(R.id.chapterStatusIcon);
                        Intent data = result.getData();
                        Chapter chapter = (Chapter) Objects.requireNonNull(data).getExtras().getSerializable(Chapter.CHAPTER_PREFERENCE_STRING);
                        if(chapter == null) return;
                        chapter.queryAndDisplayStatus(icon, CoursesActivity.this);
                        if(!data.getExtras().containsKey(Exam.EXAM_PREFERENCE_STRING)) return; //no exam, stop updating
                        Exam exam = (Exam)data.getExtras().getSerializable(Exam.EXAM_PREFERENCE_STRING);
                        if(updateViews.length > 1 && exam != null) { // exam view and exam object found, update them
                            exam.queryAndDisplayStatus(updateViews[1], CoursesActivity.this);
                        }
                    }
                }
        );
        //define what should happen when a task activity is finished
        taskActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        if(updateViews.length == 0) return; //should not happen
                        Intent data = result.getData();
                        ImageView taskIcon = updateViews[0].findViewById(R.id.taskStatusIcon);
                        Task task = (Task) Objects.requireNonNull(data).getExtras().getSerializable(Task.TASK_PREFERENCE_STRING);
                        if(task == null) return;
                        task.queryAndDisplayStatus(taskIcon, CoursesActivity.this);
                    }
                }
        );
        //define what should happen when an exam activity is finished
        examActivityLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if(updateViews.length == 0) return; //should not happen
                        Exam examData = (Exam) Objects.requireNonNull(data).getExtras().getSerializable(Exam.EXAM_PREFERENCE_STRING);
                        if(examData == null) return;
                        examData.queryAndDisplayStatus(updateViews[0], CoursesActivity.this); //update view will be the exam view
                        updateNextCourseDisplay(examData.getId()); //if there is a next course, and it's unlocked, update it
                    }
                }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(adView != null) adView.destroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(adView != null) adView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(adView != null) adView.resume();
        showCongratulationPrompt();
    }

    /**
     * Initialize activity UI.
     */
    private void setUpUI() {
        new FillCourseActivityTask().execute(this); //fill list view.

        toolbar = findViewById(R.id.toolbarCourses);
        setSupportActionBar(toolbar);
        DrawerLayout drawer = findViewById(R.id.drawer_layout_courses_root);
        NavigationView navigationView = findViewById(R.id.nav_view);
        toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);

        //initialize floating action button
        final FloatingActionButton fab = findViewById(R.id.dropDownAllIcon);
        setUpFloatingActionButton(fab);

        if(LearnJavaAds.LOAD_ADS) {
            int adId = LearnJavaAds.DEBUG_ADS ? R.string.ad_unit_id_banner_test : R.string.ad_unit_id_banner_courses;
            adView = LearnJavaAds.loadBannerAd(adId, findViewById(R.id.adContainer));
        } else {
            findViewById(R.id.adContainer).setVisibility(View.GONE);
        }
    }

    /**
     * Initializes the floating action button so it responds to clicks, and becomes draggable.
     * @param fab The floating action button.
     */
    private void setUpFloatingActionButton(@NonNull final FloatingActionButton fab) {
        //add action to perform on short click
        fab.setOnClickListener(this::onDropdownButtonClicked);
        //dragging will begin on long click
        fab.setOnLongClickListener(v -> {
            View.DragShadowBuilder myShadow = new View.DragShadowBuilder(fab);
            v.startDragAndDrop(null, myShadow, null, View.DRAG_FLAG_OPAQUE);
            v.setVisibility(View.INVISIBLE);
            return true;
        });
        //dragging on the root
        final View root = findViewById(R.id.coursesRoot);
        root.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_LOCATION:
                    dX = event.getX();
                    dY = event.getY();
                    break;
                case DragEvent.ACTION_DRAG_ENDED:
                    //these changes will make the position "permanent"
                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) fab.getLayoutParams();
                    params.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    params.removeRule(RelativeLayout.ALIGN_PARENT_END);
                    int leftMargin = (int)dX - fab.getWidth()/2;
                    int topMargin = (int)dY - fab.getHeight()/2;
                    params.leftMargin = leftMargin;
                    params.topMargin = topMargin;
                    fab.layout(leftMargin, topMargin, (int)dX + fab.getWidth()/2, (int)dY + fab.getHeight()/2);
                    fab.setVisibility(View.VISIBLE);
                    break;
            }
            return true;
        });
        //update initial rotation if needed
        if(!dropDown) {
            Animation rotateAnimation = new RotateAnimation(0.0f, 180.0f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            rotateAnimation.setFillAfter(true); //persist after
            rotateAnimation.setRepeatCount(0);
            rotateAnimation.setDuration(10); //near instant
            fab.startAnimation(rotateAnimation);
        }
    }

    /**
     * Called when the user clicks the dropdown floating button. By default it will open all
     * closed courses that can be opened. Then it changes to drop up mode which will close all
     * opened courses, changing back to drop down mode.
     * @param fab The floating button.
     */
    private void onDropdownButtonClicked(@NonNull final View fab) {
        //create the rotate animation
        float startAngle = dropDown ? 0.0f : 180.0f;
        float endAngle = dropDown ? 180.0f : 0.0f;
        Animation rotateAnimation = new RotateAnimation(startAngle, endAngle,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setFillAfter(true); //persist after
        rotateAnimation.setRepeatCount(0);
        rotateAnimation.setDuration(AnimationUtils.DURATION);
        //disable while animation is ongoing
        rotateAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                fab.setEnabled(false);
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                fab.setEnabled(true);
            }
            @Override
            public void onAnimationRepeat(Animation animation) { }
        });
        fab.startAnimation(rotateAnimation);

        //open/close the necessary courses
        final ListView courseSelectors = findViewById(R.id.courseSelectors);
        for(int pos = 0; pos < courseSelectors.getChildCount(); pos++) {
            //this course belongs to this selector
            final Course course = getParsedCourses().get(pos);
            //current selector
            final View courseSelector = courseSelectors.getChildAt(pos);
            //show hide view
            final View showHideView = courseSelector.findViewById(R.id.slideInView);
            //what happens depends on the drop variable
            if(dropDown) { //need to open unlocked, opened courses
               if(course.getStatus() == Status.LOCKED || course.getStatus() == Status.NOT_QUERIED) {
                   continue; //don't open locked courses
               }
               if(showHideView.getVisibility() == View.GONE) {
                   AnimationUtils.showView(showHideView); //only open if not opened
               }
            } else { //need to close opened courses
               if(showHideView.getVisibility() == View.VISIBLE) {
                   AnimationUtils.hideView(showHideView, courseSelector.findViewById(R.id.courseNameBar)); //only hide if visible
               }
            }
        }
        dropDown = !dropDown; //update drop down mode
    }

    /**
     * @return This object can launch a {@link ChapterActivity}.
     */
    public ActivityResultLauncher<Intent> getChapterActivityLauncher() {
        return chapterActivityLauncher;
    }

    /**
     * @return This object can launch a {@link TaskActivity}.
     */
    public ActivityResultLauncher<Intent> getTaskActivityLauncher() {
        return taskActivityLauncher;
    }

    /**
     * @return This object can launch an {@link ExamActivity}.
     */
    public ActivityResultLauncher<Intent> getExamActivityLauncher() {
        return examActivityLauncher;
    }

    /**
     * Helper method, called when an exam finishes and the user returns to the course activity
     * @param currentExamId The id of the exam that finished.
     */
    private void updateNextCourseDisplay(int currentExamId) {
        int i;
        for(i=0; i<PARSED_COURSES.size(); i++) {
            if(PARSED_COURSES.get(i).getExam().getId() == currentExamId) break;
        }
        if(i < PARSED_COURSES.size() - 1) { //there is a course after this one
            Course nextCourse = PARSED_COURSES.get(i+1);
            ListView listView = findViewById(R.id.courseSelectors);
            View nextCourseView = listView.getChildAt((i+1) - listView.getFirstVisiblePosition());
            if(nextCourseView != null) { //next course view is null when it isn't visible, but then it gets updated when it appears
                nextCourse.queryAndDisplayStatus(nextCourseView.findViewById(R.id.statusIconView), this, nextCourseView.findViewById(R.id.slideInView));
            }
        }
    }

    /**
     * Settings button (in the toolbar) click handler.
     */
    public void settingsOnClick(View v) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout_courses_root);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        DrawerUtils.handleDrawerOnClick(this, item, R.id.drawer_layout_courses_root);
        return true;
    }

    public static final String CONGRATULATION_PROMPT = "congratulation_prompt";

    /**
     * Shows a dialog that congratulates the user upon completing all exams. By default, it comes on every
     * time the courses activity opens and the user finished all exams, but can be turned off.
     * <p>
     *     The {@link CourseStatus#getCourseCount()} method handles the reactivation of this dialog, when a new
     *     course has been added.
     * </p>
     * <p>
     *     See {@link com.gaspar.learnjava.asynctask.ShowCongratulationTask} for implementation.
     * </p>
     */
    private void showCongratulationPrompt() {
        new ShowCongratulationTask().execute(this);
    }

    /**
     * @return A list of courses known to the application.
     */
    public static synchronized List<Course> getParsedCourses() {
        return PARSED_COURSES;
    }

    /**
     * Set a new course list.
     * @param parsedCourses The new course list.
     */
    public static synchronized void setParsedCourses(List<Course> parsedCourses) {
        PARSED_COURSES = parsedCourses;
    }

    /**
     * @return True only if the courses have not yet been parsed from XML.
     */
    public static boolean coursesNotParsed() {
        return PARSED_COURSES.isEmpty();
    }

    /**
     * The views that will be updated when the ongoing activity finished.
     * @see UpdatableActivity
     */
    private View[] updateViews;

    @Override
    public void setUpdateViews(View... updateViews) {
        this.updateViews = updateViews;
    }

}
