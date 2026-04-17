package com.cherry.wakeupschedule

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cherry.wakeupschedule.adapter.SchoolAdapter

class SchoolListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SchoolAdapter

    // 支持的学校列表 - 基于WakeUp课程表官方支持的学校
    private val schools = listOf(
        // 河南省高校
        School("河南医药大学（原新乡医学院）", "https://qgjw.xxmu.edu.cn/cas/login.action"),
        School("河南师范大学", "https://jwc.htu.edu.cn"),
        School("河南科技学院", "http://jwgl.hist.edu.cn/cas/login.action"),
        School("新乡学院", "https://jw.xxu.edu.cn/eams/homeExt.action"),
        School("河南工学院", "https://jwnew.hait.edu.cn/hngxyjw/cas/login.action"),
        School("郑州大学", "http://jw.zzu.edu.cn"),
        School("河南大学", "https://grsmt.henu.edu.cn/grsmt/login?loginType=student"),
        School("河南理工大学", "http://jwc.hpu.edu.cn"),
        School("河南工业大学", "http://jwc.haut.edu.cn"),
        School("河南科技大学", "http://jwc.haust.edu.cn"),
        School("河南农业大学", "https://jw.henau.edu.cn/cas/login.action"),
        School("河南财经政法大学", "http://jwc.huel.edu.cn"),
        School("华北水利水电大学", "http://jwc.ncwu.edu.cn"),
        School("河南中医药大学", "http://jwc.hactcm.edu.cn"),
        School("郑州轻工业大学", "http://jwc.zzuli.edu.cn"),
        School("中原工学院", "http://jwc.zut.edu.cn"),
        School("信阳师范大学", "http://jwc.xynu.edu.cn"),
        School("安阳师范学院", "http://jwc.aynu.edu.cn"),
        School("南阳师范学院", "http://jwc.nynu.edu.cn"),
        School("洛阳师范学院", "http://jwc.lynu.edu.cn"),
        School("商丘师范学院", "http://jwc.sqnu.edu.cn"),
        School("周口师范学院", "http://jwc.zknu.edu.cn"),
        School("许昌学院", "http://jwc.xcu.edu.cn"),
        School("平顶山学院", "http://jwc.pdsu.edu.cn"),
        School("黄淮学院", "http://jwc.huanghuai.edu.cn"),
        School("安阳工学院", "http://jwc.ayit.edu.cn"),
        School("南阳理工学院", "http://jwc.nyist.edu.cn"),
        School("河南工程学院", "http://jwc.haue.edu.cn"),
        School("洛阳理工学院", "http://jwc.lit.edu.cn"),
        School("河南警察学院", "http://jwc.hnp.edu.cn"),
        School("郑州师范学院", "http://jwc.zznu.edu.cn"),
        School("河南财政金融学院", "http://jwc.hafu.edu.cn"),
        School("河南城建学院", "http://jwc.hncj.edu.cn"),
        School("河南牧业经济学院", "http://jwc.hnuahe.edu.cn"),
        School("郑州工程技术学院", "http://jwc.zzut.edu.cn"),
        School("河南科技学院新科学院", "http://jw.xkxkxy.com"),
        School("中原科技学院", "http://jw.zykjxy.edu.cn"),
        School("新乡医学院三全学院", "http://jw.sqxy.edu.cn"),
        School("信阳学院", "http://jw.xyu.edu.cn"),
        School("安阳学院", "http://jw.ayxy.edu.cn"),
        School("商丘学院", "http://jw.squ.net.cn"),
        School("郑州工商学院", "http://jw.ztbu.edu.cn"),
        School("郑州工业应用技术学院", "http://jw.zzgyxy.edu.cn"),
        School("黄河科技学院", "http://jw.hhstu.edu.cn"),
        School("郑州科技学院", "http://jw.zit.edu.cn"),
        School("郑州升达经贸管理学院", "http://jw.shengda.edu.cn"),
        School("郑州西亚斯学院", "http://jw.sias.edu.cn"),
        School("河南开封科技传媒学院", "http://jw.humc.edu.cn"),
        School("新乡工程学院", "http://jw.xxgcxy.edu.cn"),
        School("郑州经贸学院", "http://jw.zzjmxy.edu.cn")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_school_list)

        // 设置工具栏
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "选择学校"

        setupRecyclerView()
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

    private fun openWebView(schoolName: String, url: String) {
        val intent = Intent(this, WebViewActivity::class.java)
        intent.putExtra("url", url)
        intent.putExtra("schoolName", schoolName)
        startActivity(intent)
    }
}

data class School(val name: String, val url: String)
