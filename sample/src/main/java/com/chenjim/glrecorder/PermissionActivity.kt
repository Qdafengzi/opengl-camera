package com.chenjim.glrecorder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

class PermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 先判断有没有权限
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Android VERSION  R OR ABOVE，HAVE MANAGE_EXTERNAL_STORAGE GRANTED!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Android VERSION  R OR ABOVE，NO MANAGE_EXTERNAL_STORAGE GRANTED!", Toast.LENGTH_LONG).show();
                val  intent =  Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + this.packageName))
                startActivityForResult(intent, 500)
            }
        }

        startActivity(Intent(this@PermissionActivity, MainActivity::class.java))

//        XXPermissions.with(this)
//            .permission(Permission.CAMERA)
//            .permission(Permission.RECORD_AUDIO)
//            .permission(Permission.MANAGE_EXTERNAL_STORAGE)
//            .request { _, all ->
//                if (all) {
//                    startActivity(Intent(this@PermissionActivity, MainActivity::class.java))
//                } else {
//                    Toast.makeText(this@PermissionActivity, "权限异常", Toast.LENGTH_LONG).show()
//                }
////                finish()
//            }
    }
}