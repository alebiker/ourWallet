package it.abapp.mobile.shoppingtogether;

import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ListView;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import it.abapp.mobile.shoppingtogether.model.ShopList;
import it.abapp.mobile.shoppingtogether.model.ShopListEntry;

import static android.support.test.espresso.core.deps.guava.base.Preconditions.checkNotNull;

public class Matchers {

  public static Matcher<View> withListSize (final int size) {
    return new TypeSafeMatcher<View>() {
      @Override public boolean matchesSafely (final View view) {
        return ((ListView) view).getCount() == size;
      }

      @Override public void describeTo (final Description description) {
        description.appendText ("ListView should have " + size + " items");
      }
    };
  }

  public static Matcher<View> hasAllObjects(final Matcher<ShopListEntry> shopListEntryMatcher) {
    return new TypeSafeMatcher<View>() {
      @Override public boolean matchesSafely (final View view) {
        if (!(view instanceof AdapterView))
          return false;

        Adapter adapter = ((AdapterView) view).getAdapter();
        for (int i = 0; i < adapter.getCount(); i++){
          if (!shopListEntryMatcher.matches(adapter.getItem(i)))
            return false;
        }
        return true;
      }

      @Override public void describeTo (final Description description) {
        description.appendText ("ListView should have all items with default name");
      }
    };
  }

  public static Matcher<ShopListEntry> defaultNameShopList(){
    return new BaseMatcher<ShopListEntry>() {
      @Override
      public boolean matches(Object item) {
        checkNotNull(item);
        if (!(item instanceof ShopListEntry))
          return false;

        ShopListEntry entry = (ShopListEntry)item;
        return entry.getName().contentEquals("Item");
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("with default name");
      }
    };
  }
}