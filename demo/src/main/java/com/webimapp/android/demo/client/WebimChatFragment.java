package com.webimapp.android.demo.client;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.webimapp.android.demo.client.util.EndlessScrollListener;
import com.webimapp.android.sdk.Department;
import com.webimapp.android.sdk.Message;
import com.webimapp.android.sdk.MessageListener;
import com.webimapp.android.sdk.MessageStream;
import com.webimapp.android.sdk.MessageTracker;
import com.webimapp.android.sdk.Operator;
import com.webimapp.android.sdk.WebimError;
import com.webimapp.android.sdk.WebimSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.webimapp.android.sdk.Message.Type.FILE_FROM_VISITOR;
import static com.webimapp.android.sdk.Message.Type.VISITOR;

public class WebimChatFragment extends Fragment {
    private static final int FILE_SELECT_CODE = 0;

    private WebimSession session;
    private ListController listController;
    private Message inEdit = null;
    private Message quotedMessage = null;

    private EditText editTextMessage;
    private ImageButton sendButton;
    private ImageButton editButton;

    private LinearLayout replyLayout;
    private TextView textSenderName;
    private TextView textReplyMessage;
    private int replyMessagePosition;
    private TextView textReplyId;

    private RelativeLayout chatMenuLayout;
    private RelativeLayout rateOperatorLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        if (this.session == null) {
            throw new IllegalStateException("this.session == null; Use setWebimSession before");
        }
        View rootView = inflater.inflate(R.layout.fragment_webim_chat, container, false);

        initSessionStreamListeners(rootView);
        initOperatorState(rootView);
        initChatView(rootView);
        initEditText(rootView);
        initSendButton(rootView);
        initEditButton(rootView);
        initChatMenu(rootView);
        initReplyLayout(rootView);
        initDeleteReply(rootView);
        initReplyMessageButton(rootView);

        ViewCompat.setElevation(rootView.findViewById(R.id.linLayEnterMessage), 2);
        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        session.resume();
        session.getStream().startChat();
        session.getStream().setChatRead();
    }

    @Override
    public void onStop() {
        session.pause();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        session.destroy();
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        if (getActivity() != null) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle("");
            }
        }
        super.onDetach();
    }

    private void initSessionStreamListeners(final View rootView) {
        session.getStream().setCurrentOperatorChangeListener(
                new MessageStream.CurrentOperatorChangeListener() {
                    @Override
                    public void onOperatorChanged(@Nullable Operator oldOperator,
                                                  @Nullable Operator newOperator) {
                        settingRateOperatorLayout(
                                rootView,
                                newOperator != null);

                        setOperatorAvatar(rootView, newOperator);

                        TextView operatorNameView = rootView.findViewById(R.id.action_bar_subtitle);
                        String operatorName = newOperator == null
                                ? getString(R.string.no_operator)
                                : newOperator.getName();
                        operatorNameView.setText(getString(R.string.operator_name, operatorName));
                    }
                });

        session.getStream().setVisitSessionStateListener(new MessageStream.VisitSessionStateListener() {
            @Override
            public void onStateChange(@NonNull MessageStream.VisitSessionState previousState,
                                      @NonNull MessageStream.VisitSessionState newState) {
                if (newState == MessageStream.VisitSessionState.DEPARTMENT_SELECTION) {
                    final List<Department> departmentList = session.getStream().getDepartmentList();
                    if (departmentList != null) {
                        final ArrayList<String> departmentNames = new ArrayList<>();
                        for (Department department : departmentList) {
                            departmentNames.add(department.getName());
                        }

                        AlertDialog.Builder builder = new AlertDialog.Builder(rootView.getContext());
                        builder.setTitle(R.string.choose_department)
                                .setItems(
                                        departmentNames.toArray(new String[0]),
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                session.getStream().startChatWithDepartmentKey(
                                                        departmentList.get(which).getKey());
                                            }
                                        })
                                .setNegativeButton(
                                        R.string.cancel,
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int id) {
                                            }
                                        });

                        final AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                }
            }
        });

        session.getStream().setChatStateListener(new MessageStream.ChatStateListener() {
            @Override
            public void onStateChange(@NonNull MessageStream.ChatState oldState,
                                      @NonNull MessageStream.ChatState newState) {
                if (newState == MessageStream.ChatState.CLOSED_BY_OPERATOR
                        || newState == MessageStream.ChatState.CLOSED_BY_VISITOR) {
                    showRateOperatorDialog();
                }
            }
        });
    }

    private void initOperatorState(final View rootView) {
        if (getActivity() != null) {
            ((AppCompatActivity) getActivity())
                    .setSupportActionBar((Toolbar) rootView.findViewById(R.id.toolbar));
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
            }
        }

        final TextView typingView = rootView.findViewById(R.id.action_bar_subtitle);
        Operator currentOp = session.getStream().getCurrentOperator();
        String operatorName = currentOp == null
                ? getString(R.string.no_operator)
                : currentOp.getName();
        typingView.setText(getString(R.string.operator_name, operatorName));

        session.getStream().setOperatorTypingListener(new MessageStream.OperatorTypingListener() {
            @Override
            public void onOperatorTypingStateChanged(boolean isTyping) {
                ImageView imageView = rootView.findViewById(R.id.image_typing);
                imageView.setBackgroundResource(R.drawable.typing_animation);
                AnimationDrawable animationDrawable = (AnimationDrawable) imageView.getBackground();
                Operator currentOp = session.getStream().getCurrentOperator();
                String operatorName = currentOp == null
                        ? getString(R.string.no_operator)
                        : currentOp.getName();
                if (isTyping) {
                    typingView.setText(getString(R.string.operator_typing));
                    typingView.setTextColor(
                            rootView.getResources().getColor(R.color.colorTexWhenTyping));
                    imageView.setVisibility(View.VISIBLE);
                    animationDrawable.start();
                } else {
                    typingView.setText(getString(R.string.operator_name, operatorName));
                    typingView.setTextColor(
                            rootView.getResources().getColor(R.color.white));
                    imageView.setVisibility(View.GONE);
                    animationDrawable.stop();
                }
            }
        });

        setOperatorAvatar(rootView, session.getStream().getCurrentOperator());
    }

    public void setWebimSession(WebimSession session) {
        if (this.session != null) {
            throw new IllegalStateException("Webim session is already set");
        }
        this.session = session;
    }

    private void setOperatorAvatar(View v, @Nullable Operator operator) {
        if (operator != null) {
            if (operator.getAvatarUrl() != null) {
                Glide.with(getContext())
                        .load(operator.getAvatarUrl())
                        .into((ImageView) v.findViewById(R.id.sender_photo));
            } else {
                if (getContext() != null) {
                    ((ImageView) v.findViewById(R.id.sender_photo)).setImageDrawable(
                            getContext().getResources()
                                    .getDrawable(R.drawable.default_operator_avatar));
                }
            }
            v.findViewById(R.id.sender_photo).setVisibility(View.VISIBLE);
        } else {
            v.findViewById(R.id.sender_photo).setVisibility(View.GONE);
        }
    }

    private void initChatView(View rootView) {
        ProgressBar progressBar = rootView.findViewById(R.id.loading_spinner);
        progressBar.setVisibility(View.GONE);

        RecyclerView recyclerView = rootView.findViewById(R.id.chat_recycler_view);
        recyclerView.setVisibility(View.VISIBLE);
        listController =
                ListController.install(this, recyclerView, progressBar,
                        session.getStream(), rootView);
    }

    private void initEditText(View rootView) {
        editTextMessage = rootView.findViewById(R.id.editTextChatMessage);
        editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String draft = editable.toString();
                session.getStream().setVisitorTyping(draft.isEmpty() ? null : draft);
            }
        });
    }

    private void initSendButton(View rootView) {
        sendButton = rootView.findViewById(R.id.imageButtonSendMessage);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = editTextMessage.getText().toString();
                editTextMessage.getText().clear();
                message = message.trim();
                if (!message.isEmpty()) {
                    if (BuildConfig.DEBUG && message.equals("##OPEN")) {
                        session.getStream().startChat();
                    } else {
                        if (replyLayout.getVisibility() == View.GONE) {
                            session.getStream().sendMessage(message);
                        } else {
                            replyLayout.setVisibility(View.GONE);
                            session.getStream().replyMessage(
                                    message,
                                    quotedMessage);
                        }
                    }
                }
            }
        });
    }

    private void initEditButton(View rootView) {
        editButton = rootView.findViewById(R.id.imageButtonAcceptChanges);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (inEdit == null) {
                    return;
                }

                String newText = editTextMessage.getText().toString();
                editTextMessage.getText().clear();
                newText = newText.trim();
                if (!newText.isEmpty()) {
                    session.getStream().editMessage(inEdit, newText, null);
                } else {
                    session.getStream().deleteMessage(inEdit, null);
                }
                editButton.setVisibility(View.GONE);
                sendButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void initChatMenu(final View rootView) {
        final ImageButton chatMenuButton = rootView.findViewById(R.id.imageButtonChatMenu);
        chatMenuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (chatMenuLayout.getVisibility() == View.GONE) {
                    Animation animationScaleUp =
                            AnimationUtils.loadAnimation(getContext(), R.anim.scale_up);
                    animationScaleUp.setAnimationListener(new Animation.AnimationListener(){
                        @Override
                        public void onAnimationStart(Animation arg0) {
                        }
                        @Override
                        public void onAnimationRepeat(Animation arg0) {
                        }
                        @Override
                        public void onAnimationEnd(Animation arg0) {
                            LinearLayout chatMenuBackground =
                                    rootView.findViewById(R.id.chat_menu_background);
                            Animation animationAlfaHide =
                                    AnimationUtils.loadAnimation(getContext(), R.anim.alfa_hide);
                            chatMenuBackground.startAnimation(animationAlfaHide);
                            chatMenuBackground.setVisibility(View.VISIBLE);
                        }
                    });
                    chatMenuLayout.startAnimation(animationScaleUp);
                    chatMenuLayout.setVisibility(View.VISIBLE);
                    Animation animationRotateShow =
                            AnimationUtils.loadAnimation(getContext(), R.anim.rotate_show);
                    chatMenuButton.startAnimation(animationRotateShow);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        replyLayout.setVisibility(View.GONE);
                    }
                } else {
                    hideChatMenu(rootView);
                }
            }
        });
        chatMenuLayout = rootView.findViewById(R.id.chat_menu);
        RelativeLayout newAttachmentLayout = rootView.findViewById(R.id.relLay_new_attachment);
        rateOperatorLayout = rootView.findViewById(R.id.relLay_rate_operator);
        chatMenuLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideChatMenu(rootView);
            }
        });
        newAttachmentLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideChatMenu(rootView);
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                try {
                    if (getContext() != null) {
                        startActivityForResult(
                                Intent.createChooser(
                                        intent, getContext().getString(R.string.file_chooser_title)),
                                        FILE_SELECT_CODE);
                    }
                } catch (android.content.ActivityNotFoundException e) {
                    if (getContext() != null) {
                        Toast.makeText(
                                getContext(),
                                getContext().getString(R.string.file_chooser_not_found),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
            }
        });
        settingRateOperatorLayout(
                rootView,
                session.getStream().getCurrentOperator() != null);
    }

    private void settingRateOperatorLayout(final View rootView, final boolean operatorIsAvailable) {
        ImageView imageRateOperator = rootView.findViewById(R.id.image_rate_operator);
        TextView textRateOperator = rootView.findViewById(R.id.text_rate_operator);
        if (operatorIsAvailable) {
            rateOperatorLayout.setEnabled(true);
            imageRateOperator.setColorFilter(rootView.getResources().getColor(R.color.colorText));
            textRateOperator.setTextColor(rootView.getResources().getColor(R.color.items_border));
        } else {
            rateOperatorLayout.setEnabled(false);
            imageRateOperator.setColorFilter(rootView.getResources().getColor(R.color.colorHintText));
            textRateOperator.setTextColor(rootView.getResources().getColor(R.color.colorHintText));
        }
        rateOperatorLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (operatorIsAvailable) {
                    hideChatMenu(rootView);
                    showRateOperatorDialog();
                }
            }
        });
    }

    private void showRateOperatorDialog() {
        Operator operator = session.getStream().getCurrentOperator();
        if (operator != null) {
            final Operator.Id operatorId = operator.getId();
            int rating = session.getStream().getLastOperatorRating(operatorId);
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.rate_operator_title);
            View view = View.inflate(getContext(), R.layout.rating_bar, null);
            final RatingBar bar = view.findViewById(R.id.ratingBar);
            if (rating != 0) {
                bar.setRating(rating);
            }
            Button button = view.findViewById(R.id.ratingBarButton);
            builder.setView(view);
            final AlertDialog dialog = builder.create();
            dialog.show();
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (bar.getRating() != 0) {
                        session.getStream().rateOperator(
                                operatorId, (int) bar.getRating(), null
                        );
                    }
                }
            });
        }
    }

    private void hideChatMenu(View rootView) {
        final ImageButton attachButton = rootView.findViewById(R.id.imageButtonChatMenu);
        final LinearLayout linearLayout = rootView.findViewById(R.id.chat_menu_background);
        final Animation animationRotateHide =
                AnimationUtils.loadAnimation(getContext(), R.anim.rotate_hide);
        attachButton.startAnimation(animationRotateHide);
        linearLayout.setVisibility(View.GONE);
        chatMenuLayout.setVisibility(View.GONE);
    }

    private void initReplyLayout(View rootView) {
        replyLayout = rootView.findViewById(R.id.linLayReplyMessage);
        textSenderName = rootView.findViewById(R.id.textViewSenderName);
        textReplyMessage = rootView.findViewById(R.id.textViewReplyText);
        textReplyId = rootView.findViewById(R.id.quote_Id);
        LinearLayout replyTextLayout  = rootView.findViewById(R.id.linLayReplyBody);
        replyTextLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listController.showReplyMessage(replyMessagePosition);
            }
        });
        replyLayout.setVisibility(View.GONE);
    }

    private  void initDeleteReply(View rootView) {
        ImageView deleteReplyButton = rootView.findViewById(R.id.imageButtonReplyDelete);
        deleteReplyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                replyLayout.setVisibility(View.GONE);
            }
        });
    }

    private void initReplyMessageButton(View rootView) {
        ImageView replyButton = rootView.findViewById(R.id.imageButtonReplyMessage);
        replyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listController.showReplyMessage(replyMessagePosition);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null && getActivity() != null) {
                    String mime = getActivity().getContentResolver().getType(uri);
                    String extension = mime == null
                            ? null
                            : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                    String name = extension == null
                            ? null
                            : uri.getLastPathSegment() + "." + extension;
                    File file = null;
                    try {
                        InputStream inp = getActivity().getContentResolver().openInputStream(uri);
                        if (inp != null) {
                            file = File.createTempFile("webim",
                                    extension, getActivity().getCacheDir());
                            writeFully(file, inp);
                            Cursor cursor = getActivity().getContentResolver().query(
                                    uri,
                                    null,
                                    null,
                                    null,
                                    null);
                            if (cursor != null && cursor.moveToFirst()) {
                                name = cursor.getString(
                                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                                cursor.close();
                            }
                        }
                    } catch (IOException e) {
                        Log.e("WEBIM", "failed to copy selected file", e);
                        if (file != null) {
                            boolean fileDeleted = file.delete();
                            if (!fileDeleted) {
                                Log.w("WEBIM", "failed to deleted file " + file.getName());
                            }
                            file = null;
                        }
                    }

                    if (file != null && name != null && mime != null) {
                        final File fileToUpload = file;
                        session.getStream().sendFile(
                                fileToUpload,
                                name,
                                mime,
                                new MessageStream.SendFileCallback() {
                                    @Override
                                    public void onProgress(@NonNull Message.Id id, long sentBytes) {

                                    }

                                    @Override
                                    public void onSuccess(@NonNull Message.Id id) {
                                        deleteFile(fileToUpload);
                                    }

                                    @Override
                                    public void onFailure(@NonNull Message.Id id,
                                                          @NonNull WebimError<SendFileError> error) {
                                        deleteFile(fileToUpload);
                                        if (getContext() != null) {
                                            String message;
                                            switch (error.getErrorType()) {
                                                case FILE_TYPE_NOT_ALLOWED:
                                                    message = getContext().getString(
                                                            R.string.file_upload_failed_type);
                                                    break;
                                                case FILE_SIZE_EXCEEDED:
                                                    message = getContext().getString(
                                                            R.string.file_upload_failed_size);
                                                    break;
                                                case FILE_NAME_INCORRECT:
                                                    message = getContext().getString(
                                                            R.string.file_upload_failed_name);
                                                    break;
                                                case CHAT_NOT_STARTED:
                                                    message = getContext().getString(
                                                            R.string.file_upload_failed_no_chat);
                                                    break;
                                                case UPLOADED_FILE_NOT_FOUND:
                                                default:
                                                    message = getContext().getString(
                                                            R.string.file_upload_failed_unknown);
                                            }
                                            Toast.makeText(
                                                    getContext(),
                                                    message,
                                                    Toast.LENGTH_SHORT).show();
                                        }

                                    }
                                });
                        return;
                    }
                }

            }
            if (resultCode != Activity.RESULT_CANCELED && getContext() != null) {
                Toast.makeText(
                        getContext(),
                        getContext().getString(R.string.file_selection_failed),
                        Toast.LENGTH_SHORT
                ).show();
            }
            return;

        }
        super.onActivityResult(requestCode, resultCode, data);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.pull_in_left, R.anim.push_out_right);
        }
    }

    private static void writeFully(@NonNull File to, @NonNull InputStream from) throws IOException {
        byte[] buffer = new byte[4096];
        OutputStream out = null;
        try {
            out = new FileOutputStream(to);
            for (int read; (read = from.read(buffer)) != -1; ) {
                out.write(buffer, 0, read);
            }
        } finally {
            from.close();
            if (out != null) {
                out.close();
            }
        }
    }

    private void deleteFile(File file) {
        if (!file.delete()) {
            Log.w("WEBIM", "failed to deleted file " + file.getName());
        }
    }

    void onEditAction(Message message) {
        inEdit = message;
        sendButton.setVisibility(View.GONE);
        editButton.setVisibility(View.VISIBLE);
        editTextMessage.setText(message.getText());
        editTextMessage.setSelection(message.getText().length());
    }

    void onDeleteMessageAction(Message message) {
        session.getStream().deleteMessage(message, null);
        clearEditableMessage(message);
    }

    void onReplyMessageAction(Message message, int position) {
        quotedMessage = message;
        replyLayout.setVisibility(View.VISIBLE);
        replyMessagePosition = position;
        textSenderName.setText(
                (quotedMessage.getType() == VISITOR || quotedMessage.getType() == FILE_FROM_VISITOR)
                ? this.getResources().getString(R.string.visitor_sender_name)
                : quotedMessage.getSenderName());
        textReplyId.setText(quotedMessage.getCurrentChatId());
        Message.Attachment quotedMessageAttachment = quotedMessage.getAttachment();
        String replyMessage =
                (quotedMessageAttachment != null
                        && quotedMessageAttachment.getFileInfo().getImageInfo() != null)
                ? getResources().getString(R.string.reply_message_with_image)
                : quotedMessage.getText();
        textReplyMessage.setText(replyMessage);
    }

    void clearEditableMessage(Message message) {
        if (editButton.getVisibility() == View.VISIBLE
                && inEdit.getCurrentChatId().equals(message.getCurrentChatId())) {
            editTextMessage.getText().clear();
            editButton.setVisibility(View.GONE);
            sendButton.setVisibility(View.VISIBLE);
        }
    }

    private static class ListController implements MessageListener {
        private static final int MESSAGES_PER_REQUEST = 25;
        private final MessageTracker tracker;
        private final RecyclerView recyclerView;
        private final ProgressBar progressBar;
        private final MessagesAdapter adapter;
        private final LinearLayoutManager layoutManager;
        private final EndlessScrollListener scrollListener;

        private boolean requestingMessages;

        static ListController install(WebimChatFragment webimChatFragment,
                                      RecyclerView recyclerView,
                                      ProgressBar progressBar,
                                      MessageStream messageStream,
                                      View rootView) {
            return new ListController(webimChatFragment,
                    recyclerView,
                    progressBar,
                    messageStream,
                    rootView);
        }

        private ListController(final WebimChatFragment webimChatFragment,
                               final RecyclerView recyclerView,
                               final ProgressBar progressBar,
                               final MessageStream messageStream,
                               View view) {
            this.recyclerView = recyclerView;
            this.progressBar = progressBar;

            this.layoutManager = new LinearLayoutManager(
                    webimChatFragment.getContext(), LinearLayoutManager.VERTICAL, true);
            this.layoutManager.setStackFromEnd(false);
            this.recyclerView.setLayoutManager(layoutManager);
            this.adapter = new MessagesAdapter(webimChatFragment);

            this.recyclerView.setAdapter(this.adapter);

            this.tracker = messageStream.newMessageTracker(this);

            final FloatingActionButton downButton = view.findViewById(R.id.downButton);
            downButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    downButton.setVisibility(View.GONE);
                    recyclerView.smoothScrollToPosition(0);
                }
            });
            downButton.bringToFront();

            scrollListener = new EndlessScrollListener(10) {
                @Override
                public void onLoadMore(int totalItemsCount) {
                    requestMore();
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy < 0 && downButton.isShown()) {
                        downButton.setVisibility(View.GONE);
                    }
                }
            };
            scrollListener.setLoading(true);
            scrollListener.setDownButton(downButton);
            scrollListener.setAdapter(adapter);
            recyclerView.addOnScrollListener(scrollListener);
            requestMore(true);
        }

        private void requestMore() {
            requestMore(false);
        }

        private void requestMore(final boolean flag) {
            requestingMessages = true;
            progressBar.setVisibility(View.VISIBLE);
            if (flag) {
                recyclerView.setVisibility(View.GONE);
            }
            tracker.getNextMessages(MESSAGES_PER_REQUEST, new MessageTracker.GetMessagesCallback() {
                @Override
                public void receive(@NonNull final List<? extends Message> received) {
                    requestingMessages = false;
                    progressBar.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);

                    if (received.size() != 0) {
                        adapter.addAll(0, received);
                        adapter.notifyItemRangeInserted(adapter.getItemCount() - 1,
                                received.size());

                        if (flag) {
                            recyclerView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    recyclerView.smoothScrollToPosition(0);
                                    int itemCount = layoutManager.getItemCount();
                                    int lastItemVisible =
                                            layoutManager.findLastVisibleItemPosition() + 1;
                                    if (itemCount == lastItemVisible) {
                                        requestMore();
                                    }
                                }
                            }, 100);
                        }
                        scrollListener.setLoading(false);
                    }
                }
            });
        }

        @Override
        public void messageAdded(@Nullable Message before, @NonNull Message message) {
            int ind = (before == null) ? 0 : adapter.indexOf(before);
            if (ind <= 0) {
                adapter.add(message);
                adapter.notifyItemInserted(0);
                recyclerView.smoothScrollToPosition(0);
            } else {
                adapter.add(ind, message);
                adapter.notifyItemInserted(adapter.getItemCount() - ind - 1);
            }
        }

        @Override
        public void messageRemoved(@NonNull Message message) {
            int pos = adapter.indexOf(message);
            if (pos != -1) {
                adapter.remove(pos);
                adapter.notifyItemRemoved(adapter.getItemCount() - pos);
            }
        }

        @Override
        public void messageChanged(@NonNull Message from, @NonNull Message to) {
            int ind = adapter.lastIndexOf(from);
            if (ind != -1) {
                adapter.set(ind, to);
                adapter.notifyItemChanged(adapter.getItemCount() - ind - 1, 42);
                recyclerView.setItemAnimator(null);
            }
        }

        @Override
        public void allMessagesRemoved() {
            int size = adapter.getItemCount();
            adapter.clear();
            adapter.notifyItemRangeRemoved(0, size);
            if (!requestingMessages) {
                requestMore();
            }
        }

        private void showReplyMessage(int position) {
            recyclerView.smoothScrollToPosition(position);
        }
    }
}