/*
 */

package com.googlecode.objectify.test;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Ref;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Load;
import com.googlecode.objectify.test.LoadUpgradeTests.HasMulti.Multi;
import com.googlecode.objectify.test.LoadUpgradeTests.HasSingle.Single;
import com.googlecode.objectify.test.util.TestBase;
import com.googlecode.objectify.test.util.TestObjectify;

import static com.googlecode.objectify.test.util.TestObjectifyService.ofy;

/**
 * Tests of reloading when there are load groups - basically upgrades.
 *
 * @author Jeff Schnitzer <jeff@infohazard.org>
 */
public class LoadUpgradeTests extends TestBase
{
	/** */
	@Entity
	public static class Other {
		public @Id long id;
		public Other() {}
		public Other(long id) { this.id = id; }
	}

	Key<Other> ko0;
	Key<Other> ko1;
	Other other0;
	Other other1;

	/** */
	@BeforeMethod
	public void createTwoOthers() {
		fact.register(Other.class);
		TestObjectify ofy = fact.begin();

		other0 = new Other(123L);
		other1 = new Other(456L);

		ko0 = ofy.put(other0);
		ko1 = ofy.put(other1);
	}

	/** */
	@Entity
	public static class HasMulti {
		public static class Multi {}

		public @Id Long id;
		public @Load(Multi.class) List<Ref<Other>> multi = new ArrayList<Ref<Other>>();
	}

	/** */
	@Test
	public void testMultiNotLoaded() throws Exception
	{
		fact.register(HasMulti.class);

		HasMulti hm = new HasMulti();
		hm.multi.add(Ref.create(ko0));
		hm.multi.add(Ref.create(ko1));
		HasMulti fetched = this.putClearGet(hm);

		//Key<HasMulti> hmkey = fact.getKey(hm);

		for (Ref<Other> ref: fetched.multi)
			assertRefUninitialzied(ref);
	}

	/** */
	@Test
	public void testMultiLoaded() throws Exception
	{
		fact.register(HasMulti.class);
		TestObjectify ofy = fact.begin();

		HasMulti hm = new HasMulti();
		hm.multi.add(Ref.create(ko0));
		hm.multi.add(Ref.create(ko1));
		Key<HasMulti> hmkey = ofy.put(hm);

		ofy.clear();
		HasMulti fetched = ofy.load().group(Multi.class).key(hmkey).get();

		assert fetched.multi.get(0).get().id == other0.id;
		assert fetched.multi.get(1).get().id == other1.id;
	}

	/** */
	@Test
	public void testMultiReloaded() throws Exception
	{
		fact.register(HasMulti.class);
		TestObjectify ofy = fact.begin();

		HasMulti hm = new HasMulti();
		hm.multi.add(Ref.create(ko0));
		hm.multi.add(Ref.create(ko1));
		Key<HasMulti> hmkey = ofy.put(hm);

		ofy.clear();
		ofy.get(hmkey);	// load once
		HasMulti fetched = ofy.load().group(Multi.class).key(hmkey).get();	// upgrade with multi

		Ref<Other> m0 = fetched.multi.get(0);
		assert m0.get().id == other0.id;

		assert fetched.multi.get(1).get().id == other1.id;
	}

	/** */
	@Entity
	public static class HasSingle {
		public static class Single {}

		public @Id Long id;
		public @Load(Single.class) Ref<Other> single;
	}

	/** */
	@Test
	public void testSingleReloaded() throws Exception
	{
		fact.register(HasSingle.class);
		TestObjectify ofy = fact.begin();

		HasSingle hs = new HasSingle();
		hs.single = Ref.create(ko0);
		Key<HasSingle> hskey = ofy.put(hs);

		ofy.clear();
		ofy.get(hskey);	// load once
		HasSingle fetched = ofy.load().group(Single.class).key(hskey).get();	// upgrade with single

		assert fetched.single.get().id == other0.id;
	}

	/** */
	@Test
	public void upgradingOutsideOfATransaction() throws Exception
	{
		fact.register(HasSingle.class);

		HasSingle hs = new HasSingle();
		hs.single = Ref.create(ko0);
		final Key<HasSingle> hskey = ofy().put(hs);

		ofy().clear();

		// Load hs in a transaction, which will then propagate back to parent session
		ofy().transact(new VoidWork() {
			@Override
			public void vrun() {
				ofy().get(hskey);
			}
		});

		HasSingle fetched = ofy().load().group(Single.class).key(hskey).get();	// upgrade with single

		assert fetched.single.get().id == other0.id;
	}

	/** */
	@Test
	public void reloadingOutsideOfATransaction() throws Exception
	{
		fact.register(HasSingle.class);

		HasSingle hs = new HasSingle();
		hs.single = Ref.create(ko0);
		final Key<HasSingle> hskey = ofy().put(hs);

		ofy().clear();

		// Load hs in a transaction, which will then propagate back to parent session, including the Ref async load
		ofy().transact(new VoidWork() {
			@Override
			public void vrun() {
				ofy().load().group(Single.class).key(hskey).get();
			}
		});

		// This works even without the load group because we are loading the same object instance,
		// which has populated refs.  We don't unload refs.
		HasSingle fetched = ofy().load().key(hskey).get();

		assert fetched.single.get().id == other0.id;
	}

	/** */
	@Test
	public void reloadingOutsideOfATransaction2() throws Exception
	{
		fact.register(HasSingle.class);

		HasSingle hs = new HasSingle();
		hs.single = Ref.create(ko0);
		final Key<HasSingle> hskey = ofy().put(hs);

		ofy().clear();

		// Load hs in a transaction, which will then propagate back to parent session, including the Ref async load
		ofy().transact(new VoidWork() {
			@Override
			public void vrun() {
				ofy().load().group(Single.class).key(hskey).get();
			}
		});

		// This is different by loading the group
		HasSingle fetched = ofy().load().group(Single.class).key(hskey).get();

		assert fetched.single.get().id == other0.id;
	}
}