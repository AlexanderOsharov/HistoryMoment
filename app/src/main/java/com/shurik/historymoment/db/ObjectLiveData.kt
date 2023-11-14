package com.shurik.historymoment.db

import androidx.lifecycle.LiveData

import com.shurik.historymoment.db.Object

class ObjectLiveData: LiveData<MutableList<Object>>() {

    fun setValueToObjects(list:MutableList<Object>){
        postValue(list)
    }

    fun isEmpty(): Boolean {
        if (value.isNullOrEmpty()) return true
        return false
    }
}