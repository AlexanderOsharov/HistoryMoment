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
                    var objects_list: MutableList<Object> = ArrayList()
                    //if (vkData.size > 0) vkData.clear()
                    for (ds : DataSnapshot in dataSnapshot.getChildren()){

                        val _object: Object? = ds.getValue(Object::class.java)

                        if (_object != null) {
                            if (!_object.name.isNullOrEmpty() || !_object.hash.isNullOrEmpty() ||
                                !_object.photo?.title.isNullOrEmpty() ||
                                !_object.photo?.url.isNullOrEmpty() ||
                                !_object.address?.fulladdress.isNullOrEmpty() ||
                                !_object.address?.mapPosition?.type.isNullOrEmpty() ||
                                _object.address?.mapPosition?.coordinates?.get(0) != null ||
                                _object.address?.mapPosition?.coordinates?.get(1) != null
                            ) {
                                objects_list.add(_object)
                            }
                        }
                    }
                    //Log.e("WITH LOVE FROM DB", _vkData.toString())
                    HistoryMomentViewModel.objects.setValueToObjects(objects_list)
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
