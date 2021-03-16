package org.odk.collect.android.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.forms.Form;
import org.odk.collect.android.forms.FormsRepository;
import org.odk.collect.android.provider.FormsProviderAPI;
import org.odk.collect.android.storage.StoragePathProvider;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import static android.provider.BaseColumns._ID;
import static org.odk.collect.android.dao.FormsDao.getFormsFromCursor;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.AUTO_DELETE;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.AUTO_SEND;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.BASE64_RSA_PUBLIC_KEY;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.CONTENT_URI;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.DELETED_DATE;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.DISPLAY_NAME;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.FORM_FILE_PATH;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.FORM_MEDIA_PATH;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.GEOMETRY_XPATH;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.JR_FORM_ID;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.JR_VERSION;
import static org.odk.collect.android.provider.FormsProviderAPI.FormsColumns.SUBMISSION_URI;

public class DatabaseFormsRepository implements FormsRepository {

    private final StoragePathProvider storagePathProvider;

    public DatabaseFormsRepository() {
        storagePathProvider = new StoragePathProvider();
    }

    @Nullable
    @Override
    public Form get(Long id) {
        return queryForForm(_ID + "=?", new String[]{id.toString()});
    }

    @Nullable
    @Override
    public Form getLatestByFormIdAndVersion(String jrFormId, @Nullable String jrVersion) {
        List<Form> all = getAllByFormIdAndVersion(jrFormId, jrVersion);
        if (!all.isEmpty()) {
            return all.stream().max(Comparator.comparingLong(Form::getDate)).get();
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public Form getOneByPath(String path) {
        String selection = FORM_FILE_PATH + "=?";
        String[] selectionArgs = {new StoragePathProvider().getRelativeFormPath(path)};
        return queryForForm(selection, selectionArgs);
    }

    @Nullable
    @Override
    public Form getOneByMd5Hash(@NotNull String hash) {
        if (hash == null) {
            throw new IllegalArgumentException("null hash");
        }

        String selection = FormsProviderAPI.FormsColumns.MD5_HASH + "=?";
        String[] selectionArgs = {hash};
        return queryForForm(selection, selectionArgs);
    }

    @Override
    public List<Form> getAll() {
        return queryForForms(null, null);
    }

    @Override
    public List<Form> getAllByFormIdAndVersion(String jrFormId, @Nullable String jrVersion) {
        if (jrVersion != null) {
            return queryForForms(JR_FORM_ID + "=? AND " + JR_VERSION + "=?", new String[]{jrFormId, jrVersion});
        } else {
            return queryForForms(JR_FORM_ID + "=? AND " + JR_VERSION + " IS NULL", new String[]{jrFormId});
        }
    }

    @Override
    public List<Form> getAllNotDeletedByFormId(String jrFormId) {
        return queryForForms(JR_FORM_ID + "=? AND " + DELETED_DATE + " IS NULL", new String[]{jrFormId});
    }


    @Override
    public List<Form> getAllNotDeletedByFormIdAndVersion(String jrFormId, @Nullable String jrVersion) {
        if (jrVersion != null) {
            return queryForForms(DELETED_DATE + " IS NULL AND " + JR_FORM_ID + "=? AND " + JR_VERSION + "=?", new String[]{jrFormId, jrVersion});
        } else {
            return queryForForms(DELETED_DATE + " IS NULL AND " + JR_FORM_ID + "=? AND " + JR_VERSION + " IS NULL", new String[]{jrFormId});
        }
    }

    @Override
    public Form save(Form form) {
        final ContentValues v = new ContentValues();
        v.put(FORM_FILE_PATH, storagePathProvider.getRelativeFormPath(form.getFormFilePath()));
        v.put(FORM_MEDIA_PATH, storagePathProvider.getRelativeFormPath(form.getFormMediaPath()));
        v.put(DISPLAY_NAME, form.getDisplayName());
        v.put(JR_VERSION, form.getJrVersion());
        v.put(JR_FORM_ID, form.getJrFormId());
        v.put(SUBMISSION_URI, form.getSubmissionUri());
        v.put(BASE64_RSA_PUBLIC_KEY, form.getBASE64RSAPublicKey());
        v.put(AUTO_DELETE, form.getAutoDelete());
        v.put(AUTO_SEND, form.getAutoSend());
        v.put(GEOMETRY_XPATH, form.getGeometryXpath());

        if (form.isDeleted()) {
            v.put(DELETED_DATE, 0L);
        } else {
            v.putNull(DELETED_DATE);
        }

        FormsDao formsDao = new FormsDao();
        Uri uri = formsDao.saveForm(v);
        try (Cursor cursor = Collect.getInstance().getContentResolver().query(uri, null, null, null, null)) {
            return getFormOrNull(cursor);
        }
    }

    @Override
    public void delete(Long id) {
        String selection = _ID + "=?";
        String[] selectionArgs = {String.valueOf(id)};

        Collect.getInstance().getContentResolver().delete(CONTENT_URI, selection, selectionArgs);
    }

    @Override
    public void softDelete(Long id) {
        ContentValues values = new ContentValues();
        values.put(DELETED_DATE, System.currentTimeMillis());
        new FormsDao().updateForm(values, _ID + "=?", new String[]{id.toString()});
    }

    @Override
    public void deleteByMd5Hash(@NotNull String md5Hash) {
        String selection = FormsProviderAPI.FormsColumns.MD5_HASH + "=?";
        String[] selectionArgs = {md5Hash};

        Collect.getInstance().getContentResolver().delete(CONTENT_URI, selection, selectionArgs);
    }

    @Override
    public void deleteAll() {
        Collect.getInstance().getContentResolver().delete(CONTENT_URI, null, null);
    }

    @Override
    public void restore(Long id) {
        ContentValues values = new ContentValues();
        values.putNull(DELETED_DATE);
        new FormsDao().updateForm(values, _ID + "=?", new String[]{id.toString()});
    }

    @Nullable
    private Form queryForForm(String selection, String[] selectionArgs) {
        try (Cursor cursor = Collect.getInstance().getContentResolver().query(CONTENT_URI, null, selection, selectionArgs, null)) {
            return getFormOrNull(cursor);
        }
    }

    private List<Form> queryForForms(String selection, String[] selectionArgs) {
        try (Cursor cursor = Collect.getInstance().getContentResolver().query(CONTENT_URI, null, selection, selectionArgs, null)) {
            return FormsDao.getFormsFromCursor(cursor);
        }
    }

    private Form getFormOrNull(Cursor cursor) {
        if (cursor != null && cursor.getCount() > 0) {
            List<Form> forms = getFormsFromCursor(cursor);
            return forms.get(0);
        } else {
            return null;
        }
    }
}
