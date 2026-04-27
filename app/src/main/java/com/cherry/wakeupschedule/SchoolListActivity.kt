package com.cherry.wakeupschedule

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cherry.wakeupschedule.adapter.SchoolAdapter

class SchoolListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SchoolAdapter

    private val schools = listOf(
        School("河南医药大学", "https://qgjw.xxmu.edu.cn/cas/login.action"),
        School("河南师范大学", "https://jwc.htu.edu.cn"),
        School("新乡学院", "https://jw.xxu.edu.cn/eams/homeExt.action"),
        School("河南工学院", "https://jwnew.hait.edu.cn/hngxyjw/cas/login.action"),
        School("河南大学", "https://grsmt.henu.edu.cn/grsmt/login?loginType=student"),
        School("河南农业大学", "https://jw.henau.edu.cn/cas/login.action"),
        School("安徽信息工程学院", "http://teach.aiit.edu.cn/xtgl/login_slogin.html"),
        School("安徽农业大学", "http://newjwxt.ahau.edu.cn/jwglxt"),
        School("安徽大学", "http://xk2.ahu.cn/default2.aspx"),
        School("安徽工业大学", "http://jwxt.ahut.edu.cn/jsxsd/"),
        School("安徽建筑大学", "http://219.231.0.156/"),
        School("安徽财经大学", "http://210.46.97.122/"),
        School("保定学院", "http://jwgl.bdu.edu.cn/xtgl/login_slogin.html"),
        School("北京信息科技大学", "http://jwgl.bistu.edu.cn/"),
        School("北京化工大学", "http://jwglxt.buct.edu.cn/"),
        School("北京大学", "http://elective.pku.edu.cn"),
        School("北京工业大学", "http://gdjwgl.bjut.edu.cn/"),
        School("北京师范大学珠海分校", "http://es.bnuz.edu.cn/"),
        School("北京林业大学", "http://newjwxt.bjfu.edu.cn/"),
        School("北京理工大学", "http://jwms.bit.edu.cn/"),
        School("北京理工大学珠海学院", "http://e.zhbit.com/jsxsd/"),
        School("北京联合大学", "http://jwgl.buu.edu.cn/"),
        School("北京邮电大学", "https://jwxt.bupt.edu.cn/"),
        School("渤海大学", "http://jw.bhu.edu.cn/"),
        School("滨州医学院", "http://jwgl.bzmc.edu.cn/jwglxt/xtgl/login_slogin.html"),
        School("常州机电职业技术学院", "http://jwc.czmec.cn/"),
        School("成都理工大学工程技术学院", "http://110.189.108.15/"),
        School("重庆三峡学院", "http://jwgl.sanxiau.edu.cn/"),
        School("重庆交通大学", "http://jwgl.cqjtu.edu.cn/jsxsd/"),
        School("重庆交通职业学院", "http://jwgl.cqvc.edu.cn/"),
        School("重庆大学城市科技学院", "http://jw.cqcst.edu.cn/"),
        School("重庆邮电大学移通学院", "http://222.179.134.225:81/"),
        School("长春大学", "http://cdjwc.ccu.edu.cn/jsxsd/"),
        School("长沙医学院", "http://jiaowu.csmu.edu.cn:8099/jsxsd/"),
        School("长沙理工大学", "http://xk.csust.edu.cn/"),
        School("东北林业大学", "http://jwcnew.nefu.edu.cn/dblydx_jsxsd/"),
        School("东北财经大学", "http://202.199.165.159/"),
        School("东北石油大学", "http://jwgl.nepu.edu.cn/"),
        School("大庆师范学院", "http://dqsyjwx.edu.cn/"),
        School("大连外国语大学", "http://cas.dlufl.edu.cn/cas/"),
        School("大连大学", "http://202.199.155.33/default2.aspx"),
        School("大连工业大学艺术与信息工程学院", "http://www.caie.org/page_556.shtml"),
        School("德州学院", "http://jw.dzu.edu.cn/"),
        School("电子科技大学中山学院", "http://jwgln.zsc.edu.cn/jsxsd/"),
        School("佛山科学技术学院", "http://100.fosu.edu.cn/"),
        School("福建农林大学", "http://jwgl.fafu.edu.cn"),
        School("福建农林大学金山学院", "http://jsxyjwgl.fafu.edu.cn/"),
        School("福建工程学院", "https://jwxtwx.fjut.edu.cn/jwglxt/"),
        School("福建师范大学", "http://jwglxt.fjnu.edu.cn/xtgl/login_slogin.html"),
        School("广东外语外贸大学", "http://jxgl.gdufs.edu.cn/jsxsd/"),
        School("广东工业大学", "http://jxfw.gdut.edu.cn/"),
        School("广东海洋大学", "http://210.38.137.126:8016/default2.aspx"),
        School("广东环境保护工程职业学院", "http://113.107.254.7/"),
        School("广东科学技术职业学院", "http://jw.gdk.edu.cn/"),
        School("广东财经大学", "http://jwxt.gdufe.edu.cn/"),
        School("广东金融学院", "http://jwxt.gduf.edu.cn/"),
        School("广州医科大学", "http://jw.gzuhu.edu.cn/"),
        School("广州大学", "http://jw.gzhu.edu.cn/"),
        School("广西大学", "http://jwxt2018.gxu.edu.cn/jwglxt/xtgl/"),
        School("广西大学行健文理学院", "http://210.36.24.21:9017/jwglxt/xtgl"),
        School("广西师范学院", "http://172.16.130.25/dean/student/login"),
        School("硅湖职业技术学院", "http://jw.glxy.edu.cn/"),
        School("贵州财经大学", "http://jwc.gzufe.edu.cn/"),
        School("华东理工大学", "https://inquiry.ecust.edu.cn/jsxsd/"),
        School("华中农业大学", "http://jwgl.hzau.edu.cn/xtgl/login_slogin.html"),
        School("华中师范大学", "http://one.ccnu.edu.cn/"),
        School("华中科技大学", "http://xk.hust.edu.cn/"),
        School("华北电力大学科技学校", "http://202.204.74.178/"),
        School("华南农业大学", "http://202.116.160.170/default2.aspx"),
        School("华南理工大学", "http://xsjw2018.scuteo.com"),
        School("哈尔滨商业大学", "http://jwxsd.hrbcu.edu.cn/"),
        School("哈尔滨工程大学", "http://jw.hrbeu.edu.cn/"),
        School("杭州医学院", "http://edu.hmc.edu.cn/"),
        School("杭州电子科技大学", "http://jxgl.hdu.edu.cn/"),
        School("河北大学", "http://zhjw.hbu.edu.cn/"),
        School("河北工程大学", "http://219.148.85.172:9111/login"),
        School("河北师范大学", "http://jwgl.hebtu.edu.cn/xtgl/"),
        School("河北政法职业学院", "http://jwxt.helc.edu.cn/xtgl/login_slogin.html"),
        School("河北环境工程学院", "http://jw.hebuee.edu.cn/xtgl/login_slogin.html"),
        School("河北科技师范学院", "http://121.22.25.47/"),
        School("河北经贸大学", "http://222.30.218.44/default2.aspx"),
        School("河北金融学院", "http://jw.hbfi.edu.cn/"),
        School("河南工程学院", "http://125.219.48.18/"),
        School("河南理工大学", "http://jw.hpu.edu.cn/"),
        School("河南财经政法大学", "http://xk.huel.edu.cn/jwglxt/xtgl/login_slogin.html"),
        School("河海大学", "http://202.119.113.135/"),
        School("海南大学", "http://jxgl.hainu.edu.cn/"),
        School("海南师范大学", "http://210.37.0.16/"),
        School("淮南师范学院", "http://211.70.176.173/jwglxt/xtgl/"),
        School("湖北中医药大学", "http://jwxt.hbtcm.edu.cn/jwglxt/xtgl"),
        School("湖北医药学院", "http://jw.hbmu.edu.cn"),
        School("湖北工程学院新技术学院", "http://jwglxt.hbeutc.cn:20000/jwglxt/xtgl"),
        School("湖北师范大学", "http://jwxt.hbnu.edu.cn/xtgl/login_slogin.html"),
        School("湖北经济学院", "http://jw.hbue.edu.cn/"),
        School("湖南信息职业技术学院", "http://my.hniu.cn/jwweb/ZNPK/KBFB_ClassSel.aspx"),
        School("湖南农业大学", "http://jwc.hunau.edu.cn/xsxk/"),
        School("湖南商学院", "http://jwgl.hnuc.edu.cn/"),
        School("湖南城市学院", "http://58.47.143.9:2045/zfca/login"),
        School("湖南工业大学", "http://218.75.197.123:83/"),
        School("湖南工商大学", "http://jwgl.hnuc.edu.cn/"),
        School("湖南工学院", "http://jwgl.hnit.edu.cn/"),
        School("湖南理工学院", "http://bkjw.hnist.cn/login"),
        School("湖南科技大学", "http://kdjw.hnust.cn:8080/kdjw"),
        School("湖南科技大学潇湘学院", "http://xxjw.hnust.cn:8080/xxjw/"),
        School("贺州学院", "http://jwglxt.hzu.gx.cn/jwglxt/xtgl/login_slogin.html"),
        School("黄冈师范学院", "http://hgxy.edu.cn/"),
        School("黑龙江外国语学院", "http://jw.hrwgy.edu.cn/"),
        School("吉林大学", "http://jw.jlu.edu.cn/"),
        School("吉林师范大学", "http://jwxt.jlnu.edu.cn/"),
        School("吉林建筑大学", "http://jw.jluat.edu.cn/"),
        School("吉首大学", "http://jwxt.jsu.edu.cn/"),
        School("嘉兴学院南湖学院", "http://jwzx.zjxu.edu.cn/jwglxt/xtgl/"),
        School("江苏工程职业技术学院", "http://tyjw.tmu.edu.cn/"),
        School("江苏师范大学", "http://sdjw.jsnu.edu.cn/"),
        School("江苏建筑职业技术学院", "http://jw.jsfpc.edu.cn/"),
        School("江苏科技大学", "http://jwgl.just.edu.cn:8080/jsxsd/"),
        School("江西中医药大学", "http://jwxt.jxutcm.edu.cn/jwglxt/xtgl/"),
        School("江西农业大学南昌商学院", "http://223.83.249.67:8080/jsxsd/"),
        School("暨南大学", "https://jwxt.jnu.edu.cn/"),
        School("济南大学", "http://jwgl4.ujn.edu.cn/jwglxt"),
        School("济南工程职业技术学院", "http://jwgc.jnvc.edu.cn/"),
        School("锦州医科大学", "http://jwgl.jzmu.edu.cn/jsxsd/"),
        School("临沂大学", "http://jwxt.lyu.edu.cn/jxd/"),
        School("辽宁工业大学", "http://jwxt.lnut.edu.cn/default2.aspx"),
        School("辽宁机电职业技术学院", "http://jwgl.lnjdp.com/"),
        School("茂名职业技术学院", "http://jwc.mmvtc.cn/"),
        School("闽南师范大学", "http://222.205.160.107/jwglxt/xtgl/login_slogin.html"),
        School("内蒙古大学", "http://jwxt.imu.edu.cn/login"),
        School("内蒙古师范大学", "http://jw.imnu.edu.cn/"),
        School("内蒙古科技大学", "http://stuzhjw.imust.edu.cn/login"),
        School("内蒙古科技大学包头师范学院", "http://jw.bttc.edu.cn/"),
        School("南京城市职业学院", "http://jw.ncc.edu.cn/jwglxt/xtgl/"),
        School("南京工业大学", "https://jwgl.njtech.edu.cn/"),
        School("南京师范大学中北学院", "http://222.192.5.246/"),
        School("南京特殊教育师范学院", "http://jw.ntjsy.edu.cn/"),
        School("南京理工大学", "http://202.119.81.112:8080/"),
        School("南宁师范大学", "http://172.16.130.25/dean/student/login"),
        School("南宁职业技术学院", "http://jwxt.ncvt.net:8088/jwglxt/"),
        School("南方医科大学", "http://zhjw.smu.edu.cn/"),
        School("南方科技大学", "http://jwxt.sustc.edu.cn/jsxsd"),
        School("南昌大学", "http://jwc104.ncu.edu.cn:8081/jsxsd/"),
        School("南昌航空大学", "http://jw.nchu.edu.cn/"),
        School("宁波工程学院", "http://jw.nbut.edu.cn/"),
        School("清华大学", "http://zhjwc.tsinghua.edu.cn/"),
        School("青岛农业大学", "http://jwglxt.qau.edu.cn/"),
        School("青岛滨海学院", "http://jwgl.qdbhu.edu.cn/jwglxt/xtgl/login_slogin.html"),
        School("青岛科技大学", "https://jw.qust.edu.cn/jwglxt.htm"),
        School("齐鲁工业大学", "http://jwxt.qlu.edu.cn/"),
        School("齐鲁师范学院", "http://jw.qlnu.edu.cn/"),
        School("齐齐哈尔大学", "http://jw.qqhr.edu.cn/"),
        School("三江学院", "http://jw.sju.edu.cn/jwglxt/xtgl/login_slogin.html"),
        School("上海大学", "http://cjwc.shu.edu.cn/"),
        School("上海海洋大学", "https://urp.shou.edu.cn/login"),
        School("四川大学锦城学院", "http://jwweb.scujcc.cn/"),
        School("四川美术学院", "http://jw.scfai.edu.cn/"),
        School("四川轻化工大学", "http://61.139.105.138/xtgl/"),
        School("山东农业大学", "http://xjw.sdau.edu.cn/jwglxt/"),
        School("山东大学威海校区", "https://portal.wh.sdu.edu.cn/"),
        School("山东师范大学", "http://www.bkjw.sdnu.edu.cn"),
        School("山东政法大学", "http://114.214.79.176/jwglxt/"),
        School("山东理工大学", "http://lgjw.sdut.edu.cn/"),
        School("山东科技大学", "http://jwgl.sdust.edu.cn/"),
        School("山东财经大学", "http://jw.sdufe.edu.cn/"),
        School("山东青年政治学院", "http://jw.sdyu.edu.cn/"),
        School("山西农业大学", "http://xsjwxt.sxau.edu.cn:7873/login"),
        School("山西工程技术学院", "http://211.82.48.36/login"),
        School("沈阳工程学院", "http://awcwea.com/jwgl.sie.edu.cn/jwgl/"),
        School("沈阳师范大学", "http://210.30.208.140/"),
        School("石家庄学院", "http://jwgl.sjzc.edu.cn/jwglxt/"),
        School("绍兴文理学院", "http://jw.usx.edu.cn/"),
        School("绍兴文理学院元培学院", "http://www.ypc.edu.cn/jwgl.htm"),
        School("苏州农业职业技术学院", "http://jw.szai.edu.cn/"),
        School("苏州大学", "http://xwgl.suda.edu.cn/"),
        School("苏州科技大学", "http://jw.usts.edu.cn/default2.aspx"),
        School("苏州科技大学天平学院", "http://tpjw.usts.edu.cn/default2.aspx"),
        School("韶关学院", "http://jwc.sgu.edu.cn/"),
        School("天津中医药大学", "http://jiaowu.tjutcm.edu.cn/jsxsd/"),
        School("天津体育学院", "http://jw.tjus.edu.cn/"),
        School("天津医科大学", "http://tyjw.tmu.edu.cn/"),
        School("天津工业大学", "http://jwpt.tjpu.edu.cn/"),
        School("五邑大学", "http://jxgl.wyu.edu.cn/"),
        School("威海职业学院", "http://jw.whvcpu.edu.cn/"),
        School("无锡太湖学院", "http://jwcnew.thxy.org/jwglxt/xtgl/login_slogin.html"),
        School("武昌首义学院", "http://syjw.wsyu.edu.cn/xtgl/"),
        School("武汉东湖学院", "http://221.232.159.27/"),
        School("武汉纺织大学", "http://jw.wtu.edu.cn/"),
        School("武汉轻工大学", "http://jwglxt.whpu.edu.cn/xtgl/"),
        School("温州医科大学", "http://jwxt.wmu.edu.cn"),
        School("渭南师范学院", "http://218.195.46.9"),
        School("潍坊学院", "http://210.44.64.154/"),
        School("潍坊职业学院", "http://jwgl.sdwfvc.cn/"),
        School("皖西学院", "http://jw.wuxu.edu.cn/"),
        School("信阳师范学院", "http://jwc.xynu.edu.cn/jxzhxxfwpt.htm"),
        School("厦门工学院", "http://jwxt.xit.edu.cn/default2.aspx"),
        School("厦门理工学院", "http://jw.xmut.edu.cn/"),
        School("徐州医科大学", "http://222.193.95.102/"),
        School("徐州幼儿师范高等专科学校", "http://222.187.124.16/"),
        School("湘潭大学", "http://jwxt.xtu.edu.cn/jsxsd/"),
        School("西北工业大学", "http://jw.nwpu.edu.cn/"),
        School("西华大学", "http://jwc.xhu.edu.cn/"),
        School("西南大学", "http://jw.swu.edu.cn/"),
        School("西南政法大学", "http://njwxt.swupl.edu.cn/jwglxt/xtgl"),
        School("西南民族大学", "http://jwxt.swun.edu.cn/"),
        School("西南石油大学", "http://jwxt.swpu.edu.cn/"),
        School("西安外事学院", "http://jw.xaiu.edu.cn/"),
        School("西安建筑科技大学", "http://xk.xauat.edu.cn/default2.aspx#a"),
        School("西安理工大学", "http://202.200.112.200/"),
        School("西安科技大学", "http://59.74.168.16:8989/"),
        School("西安邮电大学", "http://www.zfjw.xupt.edu.cn/jwglxt/"),
        School("西昌学院", "https://jwxt.xcc.edu.cn/xtgl/login_slogin.html"),
        School("云南财经大学", "http://202.203.194.2/"),
        School("延安大学", "http://jwglxt.yau.edu.cn/jwglxt/xtgl/login_slogin.html"),
        School("烟台大学", "http://xk.jwc.ytu.edu.cn/"),
        School("中南大学", "https://csujwc.its.csu.edu.cn/"),
        School("中南林业科技大学", "http://jwgl.csuft.edu.cn/"),
        School("中南财经政法大学", "http://jw.zuel.edu.cn/"),
        School("中国农业大学", "http://urpjw.cau.edu.cn/login"),
        School("中国医科大学", "http://jw.cmu.edu.cn/jwglxt/xtgl/login_slogin.html"),
        School("中国地质大学（武汉）", "http://jw.cug.edu.cn/"),
        School("中国石油大学（北京）", "http://urp.cup.edu.cn/login"),
        School("中国矿业大学", "http://jwxt.cumt.edu.cn/jwglxt/"),
        School("中国矿业大学徐海学院", "http://xhjw.cumt.edu.cn:8080/jwglxt/xtgl/"),
        School("中国药科大学", "http://jwgl.cpu.edu.cn/"),
        School("浙江万里学院", "http://jwxt.zwu.edu.cn/"),
        School("浙江农林大学", "http://115.236.84.158/xtgl"),
        School("浙江工业大学", "http://www.gdjw.zjut.edu.cn/"),
        School("浙江工业大学之江学院", "http://jwgl.zzjc.edu.cn/default2.aspx"),
        School("浙江工商大学", "http://124.160.64.163/jwglxt/xtgl/"),
        School("浙江师范大学", "http://jw.zjnu.edu.cn/"),
        School("浙江师范大学行知学院", "http://zxjw.zjnu.edu.cn/"),
        School("浙江财经大学", "http://fzjh.zufe.edu.cn/jwglxt"),
        School("郑州大学西亚斯国际学院", "http://218.198.176.111/default2.aspx"),
        School("郑州航空工业管理学院", "http://202.196.166.138/")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "选择学校"

        setupRecyclerView()
        setupApplyAdapter()
        setupCustomUrl()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = SchoolAdapter(schools) { school ->
            openWebView(school.name, school.url)
        }
        recyclerView.adapter = adapter
    }

    private fun setupApplyAdapter() {
        val llApplyAdapter = findViewById<LinearLayout>(R.id.ll_apply_adapter)
        llApplyAdapter.setOnClickListener {
            showApplyDialog()
        }
    }

    private fun setupCustomUrl() {
        val llCustomUrl = findViewById<LinearLayout>(R.id.ll_custom_url)
        llCustomUrl.setOnClickListener {
            showCustomUrlDialog()
        }
    }

    private fun showCustomUrlDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_url, null)
        val etSchoolName = dialogView.findViewById<EditText>(R.id.et_custom_school_name)
        val etUrl = dialogView.findViewById<EditText>(R.id.et_custom_url)

        AlertDialog.Builder(this)
            .setTitle("自定义教务系统")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val schoolName = etSchoolName.text.toString().trim()
                val url = etUrl.text.toString().trim()

                if (schoolName.isEmpty()) {
                    Toast.makeText(this, "请输入学校名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (url.isEmpty()) {
                    Toast.makeText(this, "请输入教务系统网址", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val validUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                    url
                } else {
                    "https://$url"
                }

                openWebView(schoolName, validUrl)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showApplyDialog() {
        val options = arrayOf("通过邮箱申请", "通过 GitHub Issue 申请")

        AlertDialog.Builder(this)
            .setTitle("申请适配")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openEmail()
                    1 -> openGitHubIssue()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openEmail() {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:Yngu196@qq.com")
                putExtra(Intent.EXTRA_SUBJECT, "申请教务系统适配")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开邮箱", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGitHubIssue() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ClassSchedule-CourseAdapter/CourseAdapter/issues/new"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebView(schoolName: String, url: String) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra("schoolName", schoolName)
        startActivity(intent)
    }
}

data class School(val name: String, val url: String)
