package com.example.vsokoltsov.uprogress.user.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.example.vsokoltsov.uprogress.R;
import com.example.vsokoltsov.uprogress.attachment.model.AttachmentModelImpl;
import com.example.vsokoltsov.uprogress.attachment.presenter.AttachmentPresenterImpl;
import com.example.vsokoltsov.uprogress.attachment.view.AttachmentView;
import com.example.vsokoltsov.uprogress.authentication.models.Attachment;
import com.example.vsokoltsov.uprogress.authentication.models.AuthorizationService;
import com.example.vsokoltsov.uprogress.common.ApplicationBaseActivity;
import com.example.vsokoltsov.uprogress.common.BaseApplication;
import com.example.vsokoltsov.uprogress.common.helpers.ImageUploadHelper;
import com.example.vsokoltsov.uprogress.common.helpers.UploadHelper;
import com.example.vsokoltsov.uprogress.common.services.ErrorResponse;
import com.example.vsokoltsov.uprogress.common.utils.RetrofitException;
import com.example.vsokoltsov.uprogress.direction_detail.popup.PopupInterface;
import com.example.vsokoltsov.uprogress.user.adapters.UserInfoListAdapter;
import com.example.vsokoltsov.uprogress.user.current.User;
import com.example.vsokoltsov.uprogress.user.current.UserItem;
import com.example.vsokoltsov.uprogress.user.current.UserRequest;
import com.example.vsokoltsov.uprogress.user.models.UserModel;
import com.example.vsokoltsov.uprogress.user.models.UserModelImpl;
import com.example.vsokoltsov.uprogress.user.popup.UserFormPopup;
import com.example.vsokoltsov.uprogress.user.presenters.UserProfilePresenter;
import com.example.vsokoltsov.uprogress.user.presenters.UserProfilePresenterImpl;
import com.example.vsokoltsov.uprogress.user.views.UserProfileView;

import org.solovyev.android.views.llm.LinearLayoutManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Created by vsokoltsov on 06.01.17.
 */

public class UserFragment extends Fragment implements PopupInterface, UserProfileView, AttachmentView, UploadHelper {
    private BaseApplication baseApplication;
    private View fragmentView;
    private User user;
    private ApplicationBaseActivity activity;
    private ImageView userAvatar;
    private List<UserItem> list = new ArrayList<UserItem>();
    private Toolbar toolbar;
    private UserFormPopup popup;
    private ProgressBar progressBar;
    UserProfilePresenter presenter;
    AttachmentPresenterImpl attachmentPresenter;
    AttachmentModelImpl attachmentModel;
    private UserInfoListAdapter adapter;
    CollapsingToolbarLayout layout;
    Uri imageUri;
    ImageUploadHelper uploadHelper;

    public static final int MEDIA_TYPE_IMAGE = 1;
    private File selectImageFile = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        baseApplication = ((BaseApplication) getActivity().getApplicationContext());
        fragmentView = inflater.inflate(R.layout.user_fragment, container, false);
        activity = (ApplicationBaseActivity) getActivity();
        UserModel model = new UserModelImpl(getActivity().getApplicationContext());
        uploadHelper = new ImageUploadHelper(this);
        attachmentModel = new AttachmentModelImpl(getActivity().getApplicationContext());
        presenter = new UserProfilePresenterImpl(this, model);
        attachmentPresenter = new AttachmentPresenterImpl(attachmentModel, this);
        loadList();
        loadUserImage();
        setElements();
        setToolbar();
        setNavigationDrawer();
        setProgressBar();
        return fragmentView;
    }


    public void setUser(User user) {
        this.user = user;
    }

    private void setElements() {
        layout = (CollapsingToolbarLayout) fragmentView.findViewById(R.id.collapsing_toolbar);
        layout.setTitle(user.getCorrectName());
        FloatingActionButton floatingActionButton = (FloatingActionButton) fragmentView.findViewById(R.id.addDirection);
        floatingActionButton.setImageDrawable(getResources().getDrawable(R.drawable.edit_icon));
        floatingActionButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.price_green)));
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popup = new UserFormPopup();
                popup.setPopupInterface(UserFragment.this);
                popup.setUser(user);
                popup.show(getActivity().getFragmentManager(), "form");
            }
        });

    }

    private void loadUserImage() {
        userAvatar = (ImageView) fragmentView.findViewById(R.id.userAvatar);
        user = AuthorizationService.getInstance().getCurrentUser();
        baseApplication.getImageHelper().setUserImage(user, userAvatar, R.drawable.empty_user);
    }

    private void loadList() {
        RecyclerView rv = (RecyclerView) fragmentView.findViewById(R.id.recyclerView);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        rv.setLayoutManager(llm);

        adapter = new UserInfoListAdapter(list);
        adapter.setList(user);
        rv.setAdapter(adapter);
    }

    private void setToolbar() {
        toolbar = (Toolbar) fragmentView.findViewById(R.id.toolbar_actionbar);
        ((ApplicationBaseActivity) getActivity()).setToolbar(toolbar);
    }

    private void setNavigationDrawer() {
        activity.setLeftNavigationBar();
    }

    private void setProgressBar() {
        progressBar =  (ProgressBar) fragmentView.findViewById(R.id.progressBar);
    }

    @Override
    public void successPopupOperation(Object obj, boolean operation) {
        UserRequest request = (UserRequest) obj;
        presenter.updateUser(user.getNick(), request);
    }

    @Override
    public void failedPopupOperation(Throwable t) {

    }

    @Override
    public void successUpdate(User user) {
        this.user = user;
        AuthorizationService.getInstance().setCurrentUser(user);
        popup.dismiss();
        layout.setTitle(user.getCorrectName());
        adapter.setList(user);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void failedUpdate(Throwable t) {
        RetrofitException error = (RetrofitException) t;
        ErrorResponse errors = null;
        try {
            errors = error.getErrorBodyAs(ErrorResponse.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        popup.firstNameWrapper.setError(errors.getFullErrorMessage("first_name"));
        popup.lastNameWrapper.setError(errors.getFullErrorMessage("last_name"));
        popup.emailWrapper.setError(errors.getFullErrorMessage("email"));
    }

    @Override
    public void startLoader() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void stopLoader() {
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.user_profile, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.photo:
                uploadHelper.cameraIntent();
                break;
            case R.id.collection:
                uploadHelper.galleryIntent();
                break;
            default: break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        uploadHelper.onActivityResult(requestCode, resultCode, data, user);
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable("file_uri", imageUri);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        imageUri = savedInstanceState.getParcelable("file_uri");
    }

    // Returns true if external storage for photos is available
    private boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    @Override
    public void successUpload(Attachment attachment) {
        baseApplication.getImageHelper().load(attachment.getUrl(), userAvatar, R.drawable.empty_user);
        AuthorizationService.getInstance().getCurrentUser().setImage(attachment);
    }

    @Override
    public void failedUpload(Throwable t) {

    }

    @Override
    public void setUploadFileData(MultipartBody.Part body, RequestBody attachmentableId, RequestBody attachmentableType) {
        attachmentPresenter.uploadImage(body, attachmentableType, attachmentableId);
    }
}
