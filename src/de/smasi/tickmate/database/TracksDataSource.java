package de.smasi.tickmate.database;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import de.smasi.tickmate.models.Tick;
import de.smasi.tickmate.models.Track;

public class TracksDataSource {

	// Database fields
	private SQLiteDatabase database;
	private DatabaseOpenHelper dbHelper;
	
	private String[] allColumns = {
			DatabaseOpenHelper.COLUMN_ID,
			DatabaseOpenHelper.COLUMN_NAME,
			DatabaseOpenHelper.COLUMN_ENABLED,
			DatabaseOpenHelper.COLUMN_DESCRIPTION,
			DatabaseOpenHelper.COLUMN_ICON
	};
	private String[] allColumnsTicks = {
			DatabaseOpenHelper.COLUMN_ID,
			DatabaseOpenHelper.COLUMN_TRACK_ID,
			DatabaseOpenHelper.COLUMN_YEAR,
			DatabaseOpenHelper.COLUMN_MONTH,
			DatabaseOpenHelper.COLUMN_DAY
	};
	
	List<Tick> ticks;

	public TracksDataSource(Context context) {
		dbHelper = new DatabaseOpenHelper(context);
	}

	public void open() throws SQLException {
		database = dbHelper.getWritableDatabase();
	}

	public void close() {
		dbHelper.close();
	}

	public void deleteTrack(Track track) {
		long id = track.getId();
		System.out.println("Track deleted with id: " + id);
		database.delete(DatabaseOpenHelper.TABLE_TRACKS,
				DatabaseOpenHelper.COLUMN_ID + " = " + id, null);
	}

	public Track getTrack(int id) {
		Cursor cursor = database.query(DatabaseOpenHelper.TABLE_TRACKS,
				allColumns, DatabaseOpenHelper.COLUMN_ID + " = " + id, null,
				null, null, null);
		cursor.moveToFirst();
		Track newTrack = cursorToTrack(cursor);
		cursor.close();
		return newTrack;
	}

	public List<Track> getMyTracks() {
		List<Track> tracks = new ArrayList<Track>();

		Cursor cursor = database.query(DatabaseOpenHelper.TABLE_TRACKS,
				allColumns, null, null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Track track = cursorToTrack(cursor);
			tracks.add(track);
			cursor.moveToNext();
		}
		// Make sure to close the cursor
		cursor.close();
		return tracks;
	}
	
	public List<Track> getActiveTracks() {
		List<Track> tracks = new ArrayList<Track>();

		Cursor cursor = database.query(DatabaseOpenHelper.TABLE_TRACKS,
				allColumns, DatabaseOpenHelper.COLUMN_ENABLED + " = 1", null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Track track = cursorToTrack(cursor);
			tracks.add(track);
			cursor.moveToNext();
		}
		// Make sure to close the cursor
		cursor.close();
		return tracks;
	}

	public void retrieveTicks(/* TODO: from, to */) {
		ticks = new ArrayList<Tick>();

		Cursor cursor = database.query(DatabaseOpenHelper.TABLE_TICKS,
				allColumnsTicks, null, null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Tick tick = cursorToTick(cursor);
			ticks.add(tick);
			cursor.moveToNext();
		}

		// Make sure to close the cursor
		cursor.close();
	}
	
	public Map<Integer, Map<Long, Integer> > retrieveTicksByMonths() {
		ticks = new ArrayList<Tick>();
		Map<Integer, Map<Long, Integer> > ret = new HashMap<Integer, Map<Long, Integer> >();

		Cursor cursor = database.query(DatabaseOpenHelper.TABLE_TICKS,
				new String[] {
    				"_track_id",
					"date(strftime('%y-%m-01', datetime(date, 'unixepoch'))) as month",
					"count(date) as count"
				}, null, null, 
				"_track_id, month", null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Integer track_id = cursor.getInt(0);
			Map<Long, Integer> map;
			if (!ret.containsKey(track_id)) {
				ret.put(track_id, new HashMap<Long, Integer>());
			}
			map = ret.get(track_id);
			map.put(cursor.getLong(1), cursor.getInt(2));
			cursor.moveToNext();
		}

		//Log.d("Tickmate", "loaded: track_id=" + cursor.getInt(0) + " @ " + cursor.getString(1) + " = " + cursor.getInt(2));
		cursor.close();
		return ret;
	}

	public List<Tick> getTicks(int track_id) {
		List<Tick> ticks = new ArrayList<Tick>();

		Cursor cursor = database.query(DatabaseOpenHelper.TABLE_TICKS,
				allColumnsTicks, DatabaseOpenHelper.COLUMN_TRACK_ID + " = " + Integer.toString(track_id),
				null, null, null, null);

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			Tick tick = cursorToTick(cursor);
			ticks.add(tick);
			cursor.moveToNext();
		}

		// Make sure to close the cursor
		cursor.close();
		
		return ticks;
	}

	public boolean isTicked(Track t, Calendar date) {
		date.clear(Calendar.HOUR);
		date.clear(Calendar.MINUTE);
		date.clear(Calendar.SECOND);
		date.clear(Calendar.MILLISECOND);
		//Log.v("Tickmate", "checking for " + t.getId() + " and " + date.toString());
		return ticks.contains(new Tick(t.getId(), date));
	}
	
	private Track cursorToTrack(Cursor cursor) {
		Track track = new Track(cursor.getString(1), cursor.getString(3));
		track.setId(cursor.getInt(0));
		track.setEnabled(cursor.getInt(2) >= 1);
		track.setIcon(cursor.getString(4));
		return track;
	}
	
	private Tick cursorToTick(Cursor cursor) {
		Calendar c = Calendar.getInstance();
		c.set(Calendar.YEAR, cursor.getInt(2));
		c.set(Calendar.MONTH, cursor.getInt(3));
		c.set(Calendar.DAY_OF_MONTH, cursor.getInt(4));
		return new Tick(cursor.getInt(1), c);
	}

	public void storeTrack(Track t) {
		ContentValues values = new ContentValues();

		values.put(DatabaseOpenHelper.COLUMN_NAME, t.getName());
		values.put(DatabaseOpenHelper.COLUMN_ENABLED, t.isEnabled() ? 1 : 0);
		values.put(DatabaseOpenHelper.COLUMN_DESCRIPTION, t.getDescription());
		values.put(DatabaseOpenHelper.COLUMN_ICON, t.getIcon());

		if (t.getId() > 0) {
			Log.d("Tickmate", "saving track id=" + t.getId());
			database.update(DatabaseOpenHelper.TABLE_TRACKS, values,
					DatabaseOpenHelper.COLUMN_ID + "=?",
					new String[] { Integer.toString(t.getId()) });
		} else {
			Log.d("Tickmate", "inserting track id=" + t.getId());
			database.insert(DatabaseOpenHelper.TABLE_TRACKS, null, values);
		}
	}

	public void setTick(Track track, Calendar date) {
		ContentValues values = new ContentValues();
		date.clear(Calendar.HOUR);
		date.clear(Calendar.MINUTE);
		date.clear(Calendar.SECOND);
		values.put(DatabaseOpenHelper.COLUMN_TRACK_ID, track.getId());
		values.put(DatabaseOpenHelper.COLUMN_YEAR,date.get(Calendar.YEAR));
		values.put(DatabaseOpenHelper.COLUMN_MONTH,date.get(Calendar.MONTH));
		values.put(DatabaseOpenHelper.COLUMN_DAY,date.get(Calendar.DAY_OF_MONTH));
		Log.d("Tickmate", "insert at " + date.get(Calendar.YEAR) + " " + date.get(Calendar.MONTH) + " " + date.get(Calendar.DAY_OF_MONTH));
		database.insert(DatabaseOpenHelper.TABLE_TICKS, null, values);
	}

	public void removeTick(Track track, Calendar date) {
		long timestamp = (long)(date.getTimeInMillis()/1000.0);
		String[] args = { Integer.toString(track.getId()),
				Integer.toString(date.get(Calendar.YEAR)),
				Integer.toString(date.get(Calendar.MONTH)),
				Integer.toString(date.get(Calendar.DAY_OF_MONTH)) };
		database.delete(DatabaseOpenHelper.TABLE_TICKS,
				DatabaseOpenHelper.COLUMN_TRACK_ID +"=? AND " +
				DatabaseOpenHelper.COLUMN_YEAR+"=? AND " + 
				DatabaseOpenHelper.COLUMN_MONTH+"=? AND " +
				DatabaseOpenHelper.COLUMN_DAY+"=? ", args);
		date.clear(Calendar.HOUR);
		date.clear(Calendar.MINUTE);
		date.clear(Calendar.SECOND);
		Log.d("Tickmate", "delete at "+timestamp);
	}

	public int getTickCount(int track_id) {
		
		Cursor cursor = database.query(DatabaseOpenHelper.TABLE_TICKS,
				new String[] {
					"count("+DatabaseOpenHelper.COLUMN_TRACK_ID+") as count"
				}, DatabaseOpenHelper.COLUMN_TRACK_ID + " = " + Integer.toString(track_id), null, 
				DatabaseOpenHelper.COLUMN_TRACK_ID , null, null);
		cursor.moveToFirst();
		int c = 0;
		if (cursor.getCount() > 0) {
			c = cursor.getInt(0);
		}
		cursor.close();		
		return c;
	}
}