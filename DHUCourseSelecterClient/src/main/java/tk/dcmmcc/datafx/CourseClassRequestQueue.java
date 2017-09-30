package tk.dcmmcc.datafx;

import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import tk.dcmmcc.utils.LoggerUtil;
import java.util.*;
import java.util.logging.Logger;

/**
 * 选课任务队
 * Created by DCMMC on 2017/9/9.
 */
public class CourseClassRequestQueue {
    //抢课次数
    private static IntegerProperty courseRequestCountProperty = new SimpleIntegerProperty(0);
    //任务队列中剩余的课程数目
    private static IntegerProperty queueCoursesNumberProperty = new SimpleIntegerProperty(0);
    //成功录取(选择)的课程数目
    private static IntegerProperty selectedCoursesNumberProperty = new SimpleIntegerProperty(0);
    //保证没有重复的课程, 存储着CourseId(保持元素的顺序)和对应的CoursesData
    private static LinkedHashMap<String, CourseData> coursesDataMap = new LinkedHashMap<>();
    //started flag
    private static BooleanProperty statedProperty = new SimpleBooleanProperty(false);
    //Logger
    private static Logger logger = Logger.getLogger(CourseClassRequestQueue.class.getName());
    //all courses finished flag
    private BooleanProperty allFinished  = new SimpleBooleanProperty(false);
    //还有多少s开始自动选课
    private IntegerProperty startAfterSecondsProperty = new SimpleIntegerProperty(0);

    static {
        LoggerUtil.initLogger(logger);
    }

    {
        //都完成的时候就停止所有线程
        allFinished.addListener(((observable, oldValue, newValue) -> {
            if (newValue)
                cancelAll();
        }));
    }

    /* Constructors */

    public CourseClassRequestQueue() {

    }

    public CourseClassRequestQueue(CourseData courseData) {
        coursesDataMap.put(courseData.getCourseId(), courseData);
        queueCoursesNumberProperty.setValue(1);
    }

    public CourseClassRequestQueue(CourseData[] courseDatas) {
        for (CourseData courseData : courseDatas) {
            coursesDataMap.put(courseData.getCourseId(), courseData);
        }
    }

    /* Methods */

    /**
     * 从队列中删除CourseId指定CourseData
     * @return 被移除的CourseData, 如果没有对应的CourseData或者已经开始选课了就返回null
     */
    public static CourseData removeCourseData(String courseId) {
        //如果已经开始就不能remove
        if (!statedProperty.get()) {
            queueCoursesNumberProperty.set(queueCoursesNumberProperty.get() - 1);

            return coursesDataMap.remove(courseId);
        } else {
            return null;
        }
    }

    /**
     * 添加课程
     * @param courseData 要添加的课程
     * @return 成功就返回添加的这个课程, 失败就返回null
     */
    public CourseData addCoursesData(CourseData courseData) {
        if (existCourse(courseData)) {
            logger.warning("CourseClassRequestQueue中已经存在了" +
                    courseData.getCourseType().getCourse().getTextTitle() + "(courseId" +
                    courseData.getCourseId() + ")" + "这个Course了!");
            return null;
        } else {
            coursesDataMap.put(courseData.getCourseId(), courseData);
            return courseData;
        }
    }

    /**
     * 判断queue中是否已经有了这个Course了
     * @param courseData 要判断的coursesData
     * @return 如果queue中已经有了该Course了, 就返回true
     */
    public boolean existCourse(CourseData courseData) {
        return coursesDataMap.containsKey(courseData.getCourseId());
    }

    /**
     * 延迟开始
     * @param date 要延迟开始的时间
     */
    public void startAll(Date date) {
        //绑定那一堆Propertys
        if (coursesDataMap.size() == 0) {
            logger.warning("选课队列为空!");
            return;
        }

        statedProperty.setValue(true);

        //记录成功的Course的个数
        SimpleIntegerProperty[] successCountProperty = new SimpleIntegerProperty[coursesDataMap.size()];

        //Pointer<Integer> indexPointer = new Pointer<>(0);
        int index = 0;
        for (CourseData courseData : coursesDataMap.values()) {
            //debug
            //System.out.println(coursesDataMap.values().size());
            //System.out.println(successCountProperty.length);
            //System.out.println(indexPointer.getP());

            //successCountProperty[indexPointer.getP()] = new SimpleIntegerProperty(0);
            successCountProperty[index] = new SimpleIntegerProperty(0);
            //copy value
            int tmp = index;
            courseData.getSelectSuccess().addListener(((observable, oldValue, newValue) -> {
                //if (newValue)
                //    successCountProperty[indexPointer.getP()].setValue(1);
                //indexPointer.setP(indexPointer.getP() + 1);
                if (newValue)
                    successCountProperty[tmp].setValue(1);
                //indexPointer.setP(indexPointer.getP() + 1);
            }));
            index++;
        }

        if (coursesDataMap.size() == 1) {
            queueCoursesNumberProperty.bind(new SimpleIntegerProperty(1)
                    .subtract(successCountProperty[0]));
            selectedCoursesNumberProperty.bind(successCountProperty[0]);
            allFinished.bind(((CourseData)coursesDataMap.values().toArray()[0]).getSelectSuccess());
            allFinished.addListener(((observable, oldValue, newValue) -> {
                if (newValue)
                    statedProperty.setValue(false);
            }));
            courseRequestCountProperty.bind(((CourseData)(coursesDataMap.values().toArray()[0]))
                    .getCourseRequestCountProperty());
        } else {
            Object[] courseDatas = (coursesDataMap.values().toArray());
            IntegerBinding courseRequestCountBinding = (IntegerBinding) ((CourseData)courseDatas[0])
                    .getCourseRequestCountProperty()
                    .add(((CourseData)courseDatas[1]).getCourseRequestCountProperty());
            BooleanBinding allFinishedBinding = ((CourseData)coursesDataMap.values().toArray()[0]).getSelectSuccess()
                    .and(((CourseData)coursesDataMap.values().toArray()[1]).getSelectSuccess());
            for (int i = 2; i < courseDatas.length; i++) {
                courseRequestCountBinding = (IntegerBinding) courseRequestCountBinding.add(
                        ((CourseData)courseDatas[i]).getCourseRequestCountProperty());
                allFinishedBinding = allFinishedBinding.and(
                        ((CourseData)courseDatas[i]).getSelectSuccess());
            }
            courseRequestCountProperty.bind(courseRequestCountBinding);

            IntegerBinding successSelected = (IntegerBinding) successCountProperty[0].add(successCountProperty[1]);

            for (int i = 2; i < successCountProperty.length; i++)
                successSelected = (IntegerBinding) successSelected.add(successCountProperty[i]);

            selectedCoursesNumberProperty.bind(successSelected);
            queueCoursesNumberProperty.bind(new SimpleIntegerProperty(successCountProperty.length)
                .subtract(successSelected));
            allFinished.bind(allFinishedBinding);
            allFinished.addListener(((observable, oldValue, newValue) -> {
                if (newValue)
                    statedProperty.setValue(false);
            }));
        }

        //设置定时器
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                //解决Timer Not on FX application thread的问题
                // TODO 将Timer替换为TimeLine
                Platform.runLater(() -> {
                    for (Object courseData : coursesDataMap.values().toArray())
                        ((CourseData)courseData).startAll();
                });
            }
        }, date);

        startAfterSecondsProperty.setValue((date.getTime() - new Date().getTime()) / 1000);

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                //解决Timer Not on FX application thread的问题
                // TODO 将Timer替换为TimeLine
                Platform.runLater(() -> timeGoesBy());
            }
        }, 0, 1000);
    }

    /**
     * 现在开始
     */
    public void startAll() {
        startAll(new Date());
    }

    /**
     * 取消所有
     */
    public void cancelAll() {
        for (CourseData courseData : coursesDataMap.values()) {
            courseData.cancelAll();
        }
    }

    private void timeGoesBy() {
        startAfterSecondsProperty.setValue(startAfterSecondsProperty.get() - 1);
    }

    /* Setters and getters */

    public static IntegerProperty getCourseRequestCountProperty() {
        return courseRequestCountProperty;
    }

    public static IntegerProperty getQueueCoursesNumberProperty() {
        return queueCoursesNumberProperty;
    }

    public static IntegerProperty getSelectedCoursesNumberProperty() {
        return selectedCoursesNumberProperty;
    }

    public static LinkedHashMap<String, CourseData> getCoursesDataMap() {
        return coursesDataMap;
    }

    public static void setCoursesDataMap(LinkedHashMap<String, CourseData> coursesDataMap) {
        CourseClassRequestQueue.coursesDataMap = coursesDataMap;
    }

    public IntegerProperty getStartAfterSecondsProperty() {
        return startAfterSecondsProperty;
    }

    public static BooleanProperty getStartedProperty() {
        return statedProperty;
    }
}///~
