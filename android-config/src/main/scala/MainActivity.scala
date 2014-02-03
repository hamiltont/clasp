package clasp.android_config

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.content.ContentProviderOperation
import _root_.android.provider.ContactsContract
import _root_.android.provider.ContactsContract.{Data,RawContacts}
import _root_.android.provider.ContactsContract.CommonDataKinds.StructuredName

import java.util.ArrayList

class MainActivity extends Activity with TypedActivity {
  // Generated with http://listofrandomnames.com
  val names = List(
    "Rod Basham", "Alvaro Reifsteck", "Ginette Vester", "Eliseo Pollock",
    "Lashon Edgemon", "Jeanette Rutledge", "Mac Mccourt", "Grover Freese",
    "Leeann Harney", "Dulce Ahner", "Raleigh Clampitt", "Bari Dinsmore",
    "Alejandra Scheffler", "Annamarie Mahle", "Hee Patnaude",
    "Crista Hernandes", "Marcela Welter", "Kaitlyn Zambrana",
    "Yolanda Woosley", "Bebe Leveille", "Trinity Hedstrom", "Mitzi Metayer",
    "Petra Clingman", "Johnetta Mcmeen", "Monnie Pantoja", "Garret Dedrick",
    "Akilah Linn", "Eilene Paquet", "Jasmin Heisler", "Sina Blansett"
  )

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)
    setContentView(R.layout.main)


    val ops = new ArrayList[ContentProviderOperation]()
    // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/provider/ContactsContract.java
    // Not sure why this can't access RawContacts.ACCOUNT_TYPE or
    // RawContacts.ACCOUNT_NAME, but hard coding works for now.
    names map { name:String =>
      val rawContactInsertIndex = ops.size
      ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
        .withValue("account_type", "person")
        .withValue("account_name", name)
        .build
      )
      ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
        .withValueBackReference("raw_contact_id", rawContactInsertIndex)
        .withValue("mimetype", StructuredName.CONTENT_ITEM_TYPE)
        .withValue(StructuredName.DISPLAY_NAME, name)
        .build
      )
    }
    getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops)
  }
}
