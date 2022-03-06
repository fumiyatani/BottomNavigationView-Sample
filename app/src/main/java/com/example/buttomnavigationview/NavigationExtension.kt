package com.example.buttomnavigationview

import android.content.Intent
import android.util.SparseArray
import androidx.core.util.forEach
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

fun BottomNavigationView.setupWithNavController(
    navGraphIds: List<Int>,
    fragmentManager: FragmentManager,
    containerId: Int,
    intent: Intent,
    /*
        ここのリスナーを渡すことで MenuItem の id を取得できるようにして外側でキャッチできるようにして、ハンドリングできるようにする
        Boolean はキャッチしたら true を返すようにする。
    */
    onNavigationItemSelectedListener: ((Int) -> Boolean)? = null,
) {
    // NavGraph ID と 独自に付与した FragmentTag を保存する.
    // BottomNavigationView の Item が選択された際に復元する NavGraph を取得するために使用する
    val graphIdToTagMap = SparseArray<String>()

    // 選択された NavGraph を LiveData として設定元に返す
    val selectedNavController = MutableLiveData<NavController>()

    // 一番左の GraphId
    var firstFragmentGraphId = 0

    // それぞれの NavGraph ID から NavHostFragment を生成
    navGraphIds.forEachIndexed { index, navGraphId ->
        // それぞれの NavGraph ID に対応した NavHostFragment に付与する Tag
        val fragmentTag = getFragmentTag(index)

        // NavHostFragment を取得する
        val navHostFragment = obtainNavHostFragment(
            fragmentManager,
            fragmentTag,
            navGraphId,
            containerId
        )

        // NavGraph 内で android:id として設定した項目
        val graphId = navHostFragment.navController.graph.id

        if (index == 0) {
            firstFragmentGraphId = graphId
        }

        graphIdToTagMap.append(graphId, fragmentTag)

        // 選択された MenuItem に紐づく NavHostFragment を attach/detach する
        if (this.selectedItemId == graphId) {
            attachNavHostFragment(
                fragmentManager,
                navHostFragment,
                index == 0
            )
        } else {
            detachNavHostFragment(
                fragmentManager,
                navHostFragment
            )
        }
    }

    // 以下は実際に BottomNavigation に設定した Item がタップされた時の Fragment 切り替えの処理
    var selectedItemTag = graphIdToTagMap.get(this.selectedItemId)
    val firstFragmentTag = graphIdToTagMap.get(firstFragmentGraphId)
    var isOnFirstFragment = selectedItemTag == firstFragmentTag

    setOnNavigationItemSelectedListener { item ->
        if (fragmentManager.isStateSaved) {
            false
        } else {
            // ここが true の場合は 画面遷移させないようにする
            if (onNavigationItemSelectedListener?.invoke(item.itemId) == true) {
                return@setOnNavigationItemSelectedListener false
            }
            val newlySelectedItemTag = graphIdToTagMap.get(item.itemId)

            if (selectedItemTag != newlySelectedItemTag) {
                fragmentManager.popBackStack(
                    firstFragmentTag,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
                val selectedFragment = fragmentManager
                    .findFragmentByTag(newlySelectedItemTag)
                    as NavHostFragment

                if (firstFragmentTag != newlySelectedItemTag) {
                    fragmentManager.beginTransaction()
                        .attach(selectedFragment)
                        .setPrimaryNavigationFragment(selectedFragment)
                        .apply {
                            graphIdToTagMap.forEach { _, fragmentTag ->
                                if (fragmentTag != newlySelectedItemTag) {
                                    // 選択された Item ではない Fragment を detach する
                                    detach(fragmentManager.findFragmentByTag(fragmentTag)!!)
                                }
                            }
                        }
                        .addToBackStack(firstFragmentTag)
                        .setCustomAnimations(
                            R.anim.nav_default_enter_anim,
                            R.anim.nav_default_exit_anim,
                            R.anim.nav_default_pop_enter_anim,
                            R.anim.nav_default_pop_exit_anim,
                        )
                        .setReorderingAllowed(true)
                        .commit()
                }
                selectedItemTag = newlySelectedItemTag
                isOnFirstFragment = selectedItemTag == firstFragmentTag
                selectedNavController.value = selectedFragment.navController
                true
            } else {
                false
            }
        }
    }
}

private fun detachNavHostFragment(
    fragmentManager: FragmentManager,
    navHostFragment: NavHostFragment
) {
    fragmentManager.beginTransaction()
        .detach(navHostFragment)
        .commitNow()
}

private fun attachNavHostFragment(
    fragmentManager: FragmentManager,
    navHostFragment: NavHostFragment,
    isPrimaryNavHostFragment: Boolean
) {
    fragmentManager.beginTransaction()
        .attach(navHostFragment)
        .apply {
            if (isPrimaryNavHostFragment) {
                setPrimaryNavigationFragment(navHostFragment)
            }
        }
        .commitNow()
}

/**
 *  NavHostFragment を取得する.
 *  もし生成されていなかったら新しく生成してから返却する.
 */
private fun obtainNavHostFragment(
    fragmentManager: FragmentManager,
    fragmentTag: String,
    navGraphId: Int,
    containerId: Int,
): NavHostFragment {
    // NavHostFragment がすでに FragmentManager 内に存在していたら取得できたものを返却する
    val existingFragment = fragmentManager.findFragmentByTag(fragmentTag) as NavHostFragment?
    existingFragment?.let { return it }

    // 存在しない場合は生成し直して返却する
    val navHostFragment =  NavHostFragment.create(navGraphId)
    fragmentManager.beginTransaction()
        .add(containerId, navHostFragment, fragmentTag)
        .commitNow()
    return navHostFragment
}

private fun getFragmentTag(index: Int): String {
    return "bottomNavigation#$index"
}