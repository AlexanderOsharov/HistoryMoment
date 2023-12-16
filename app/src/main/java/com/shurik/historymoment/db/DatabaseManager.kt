package com.shurik.historymoment.db

import android.util.Log
import com.google.firebase.database.*
import kotlinx.coroutines.*
import com.shurik.historymoment.db.Object

class DatabaseManager {

    private var db: DatabaseReference
    private val key: String = "objects"

    init{
        db = FirebaseDatabase.getInstance().getReference(key)
    }

    // Этот код достает все точки из базы и записывает в HistoryMomentViewModel
    fun getData() {
        val coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch{
            db.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
//                    var objects_list: MutableList<Object> = ArrayList()
//                    //if (vkData.size > 0) vkData.clear()
//                    for (ds : DataSnapshot in dataSnapshot.getChildren()){
//
//                        val _object: Object? = ds.getValue(Object::class.java)
//
//                        if (_object != null) {
//                            if (!_object.name.isNullOrEmpty() || !_object.hash.isNullOrEmpty() ||
//                                !_object.photo?.title.isNullOrEmpty() ||
//                                //!_object.photo?.url.isNullOrEmpty() ||
//                                !_object.address?.fulladdress.isNullOrEmpty() ||
//                                !_object.address?.mapPosition?.type.isNullOrEmpty() ||
//                                _object.address?.mapPosition?.coordinates?.get(0) != null ||
//                                _object.address?.mapPosition?.coordinates?.get(1) != null
//                            ) {
//                                val photoUrls: MutableList<String>? = when (val photoUrl = _object.photo?.url) {
//                                    is String -> listOf(photoUrl) as MutableList<String>? // Заменено на создание списка с одним значением
//                                    is Map<*, *> -> (photoUrl as Map<*, *>).values.mapNotNull { it as? String }.toMutableList()
//                                    else -> mutableListOf() // При несоответствии типу по умолчанию вернуть пустой список
//                                }
//
//                                _object.photo?.urls = photoUrls
//
//                                objects_list.add(_object)
//                            }
//                        }
//                    }
                    val objectsList: MutableList<Object> = ArrayList()
                    for (ds: DataSnapshot in dataSnapshot.getChildren()) {
                        val _object: Object? = ds.getValue(Object::class.java)
                        _object?.let { obj ->
                            obj.photo?.urls = when (val photoUrl = obj.photo?.url) {
                                is String -> mutableListOf(photoUrl)
                                is List<*> -> photoUrl.filterIsInstance<String>().toMutableList()
                                else -> mutableListOf()
                            }
                            objectsList.add(obj)
                        }
                    }
                    //Log.e("WITH LOVE FROM DB", _vkData.toString())
                    HistoryMomentViewModel.objects.setValueToObjects(objectsList)
                }
                //Log.e("SUCCESS DATABASE TAG", _vkData.toString())
                override fun onCancelled(error: DatabaseError) {
                    // Failed to read value
                    Log.e("DATABASE ERROR TAG", "Failed to read value.", error.toException())
                }
            })
        }
    }
}
