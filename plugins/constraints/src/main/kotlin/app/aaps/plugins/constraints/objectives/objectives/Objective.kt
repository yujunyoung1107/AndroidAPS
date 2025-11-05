package app.aaps.plugins.constraints.objectives.objectives

import android.content.Context
import android.text.util.Linkify
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.Preferences
import app.aaps.plugins.constraints.R
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.Runnable
import javax.inject.Inject
import kotlin.math.floor

abstract class Objective(injector: HasAndroidInjector, spName: String, @StringRes objective: Int, @StringRes gate: Int) {

    @Inject lateinit var sp: SP
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil

    private val spName: String
    @StringRes val objective: Int
    @StringRes val gate: Int
    var startedOn: Long = 0
        set(value) {
            field = value
            sp.putLong("Objectives_" + spName + "_started", startedOn)
        }
    var accomplishedOn: Long = 0
        set(value) {
            field = value
            sp.putLong("Objectives_" + spName + "_accomplished", value)
        }

    var tasks: MutableList<Task> = ArrayList()

    val isCompleted: Boolean
        get() = true  // 항상 완료 상태

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
        this.spName = spName
        this.objective = objective
        this.gate = gate
        startedOn = sp.getLong("Objectives_" + spName + "_started", 0L)
        accomplishedOn = sp.getLong("Objectives_" + spName + "_accomplished", 0L)
        if (accomplishedOn - dateUtil.now() > T.hours(3).msecs() || startedOn - dateUtil.now() > T.hours(3).msecs()) {
            startedOn = 0
            accomplishedOn = 0
        }
    }

    fun isCompleted(trueTime: Long): Boolean = true  // 항상 완료 상태

    val isAccomplished: Boolean
        get() = true  // 항상 달성 상태
    val isStarted: Boolean
        get() = true  // 항상 시작 상태

    @Suppress("unused")
    open fun specialActionEnabled(): Boolean = true

    @Suppress("unused")
    open fun specialAction(activity: FragmentActivity, input: String) {
    }

    abstract inner class Task(var objective: Objective, @StringRes val task: Int) {

        var hints = ArrayList<Hint>()
        var learned = ArrayList<Learned>()

        abstract fun isCompleted(): Boolean

        open fun isCompleted(trueTime: Long): Boolean = isCompleted()

        open val progress: String
            get() = rh.gs(R.string.completed_well_done)  // 항상 완료 메시지

        fun hint(hint: Hint): Task {
            hints.add(hint)
            return this
        }

        fun learned(learned: Learned): Task {
            this.learned.add(learned)
            return this
        }

        open fun shouldBeIgnored(): Boolean = false
    }

    inner class MinimumDurationTask internal constructor(objective: Objective, private val minimumDuration: Long) : Task(objective, R.string.time_elapsed) {

        override fun isCompleted(): Boolean = true  // 항상 완료

        override fun isCompleted(trueTime: Long): Boolean = true  // 항상 완료

        override val progress: String
            get() = rh.gs(R.string.completed_well_done)  // 항상 완료 메시지
    }

    inner class UITask internal constructor(objective: Objective, @StringRes task: Int, private val spIdentifier: String, val code: (context: Context, task: UITask, callback: Runnable) -> Unit) : Task(objective, task) {

        var answered: Boolean = false
            set(value) {
                field = value
                sp.putBoolean("UITask_$spIdentifier", value)
            }

        init {
            answered = sp.getBoolean("UITask_$spIdentifier", false)
        }

        override fun isCompleted(): Boolean = true  // 항상 완료
    }

    inner class ExamTask internal constructor(objective: Objective, @StringRes task: Int, @StringRes val question: Int, private val spIdentifier: String) : Task(objective, task) {

        var options = ArrayList<Option>()
        var answered: Boolean = false
            set(value) {
                field = value
                sp.putBoolean("ExamTask_$spIdentifier", value)
            }
        var disabledTo: Long = 0
            set(value) {
                field = value
                sp.putLong("DisabledTo_$spIdentifier", value)
            }

        init {
            answered = sp.getBoolean("ExamTask_$spIdentifier", false)
            disabledTo = sp.getLong("DisabledTo_$spIdentifier", 0L)
        }

        override fun isCompleted(): Boolean = true  // 항상 완료

        fun isEnabledAnswer(): Boolean = disabledTo < dateUtil.now()

        fun option(option: Option): ExamTask {
            options.add(option)
            return this
        }
    }

    inner class Option internal constructor(@StringRes var option: Int, var isCorrect: Boolean) {

        private var cb: CheckBox? = null

        fun generate(context: Context): CheckBox {
            cb = CheckBox(context)
            cb?.setText(option)
            return cb!!
        }

        fun evaluate(): Boolean {
            val selection = cb!!.isChecked
            return if (selection && isCorrect) true else !selection && !isCorrect
        }
    }

    inner class Hint internal constructor(@StringRes var hint: Int) {

        fun generate(context: Context): TextView {
            val textView = TextView(context)
            textView.setText(hint)
            textView.autoLinkMask = Linkify.WEB_URLS
            textView.linksClickable = true
            textView.setLinkTextColor(rh.gac(context, com.google.android.material.R.attr.colorSecondary))
            Linkify.addLinks(textView, Linkify.WEB_URLS)
            return textView
        }
    }

    inner class Learned internal constructor(@StringRes var learned: Int)
}
