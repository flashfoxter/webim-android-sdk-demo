package com.webimapp.android.demo.client;

import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.webimapp.android.demo.client.items.ViewType;
import com.webimapp.android.sdk.Message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import static com.webimapp.android.sdk.Message.Attachment;
import static com.webimapp.android.sdk.Message.FileInfo;
import static com.webimapp.android.sdk.Message.ImageInfo;
import static com.webimapp.android.sdk.Message.Quote;
import static com.webimapp.android.sdk.Message.SendStatus;
import static com.webimapp.android.sdk.Message.Type.*;

public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.MessageHolder> {
    private static final long MILLIS_IN_DAY = 1000 * 60 * 60 * 24;

    private final List<Message> messageList;
    private final WebimChatFragment webimChatFragment;

    MessagesAdapter(WebimChatFragment webimChatFragment) {
        this.webimChatFragment = webimChatFragment;
        this.messageList = new ArrayList<>();
    }

    @NonNull
    @Override
    public MessageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewType vt = ViewType.values()[viewType];
        View view = LayoutInflater.from(webimChatFragment.getContext()).inflate(getLayout(vt),
                parent, false);
        switch (vt) {
            case OPERATOR:
            case FILE_FROM_OPERATOR:
                return new ReceivedMessageHolder(view);
            case VISITOR:
            case FILE_FROM_VISITOR:
                return new SentMessageHolder(view);
            case INFO:
            case INFO_OPERATOR_BUSY:
                return new MessageHolder(view);
            default:
                return null;
        }


    }

    @Override
    public void onBindViewHolder(@NonNull MessageHolder holder, int position) {
        Message message = messageList.get(messageList.size() - position - 1);
        Message prev = position < messageList.size() - 1
                ? messageList.get(messageList.size() - position - 2)
                : null;

        if (prev != null) {
            if ((message.getType().equals(OPERATOR)
                    || message.getType().equals(FILE_FROM_OPERATOR))
                    && (prev.getType().equals(OPERATOR)
                    || prev.getType().equals(FILE_FROM_OPERATOR))
                    && prev.getOperatorId().equals(message.getOperatorId())) {
                ((ReceivedMessageHolder) holder).showSenderInfo = false;
            }
        }

        boolean showDate = false;
        if (prev == null
                || (message.getTime() / MILLIS_IN_DAY != prev.getTime() / MILLIS_IN_DAY)) {
            showDate = true;
        }

        holder.bind(message, showDate);
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(messageList.size() - position - 1);
        if (message.getSendStatus() == SendStatus.SENDING) {
            return ViewType.VISITOR.ordinal();
        }
        switch (message.getType()) {
            case CONTACT_REQUEST:
            case OPERATOR:
                return ViewType.OPERATOR.ordinal();
            case VISITOR:
                return ViewType.VISITOR.ordinal();
            case OPERATOR_BUSY:
                return ViewType.INFO_OPERATOR_BUSY.ordinal();
            case FILE_FROM_VISITOR:
                return message.getAttachment() == null || message.getAttachment().getFileInfo().getImageInfo() != null
                        ? ViewType.VISITOR.ordinal()
                        : ViewType.FILE_FROM_VISITOR.ordinal();
            case FILE_FROM_OPERATOR:
                return message.getAttachment() == null || message.getAttachment().getFileInfo().getImageInfo() != null
                        ? ViewType.OPERATOR.ordinal()
                        : ViewType.FILE_FROM_OPERATOR.ordinal();
            case INFO:
            default:
                return ViewType.INFO.ordinal();
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    private int getLayout(ViewType viewType) {
        switch (viewType) {
            case OPERATOR:
            case FILE_FROM_OPERATOR:
                return R.layout.item_message_received;
            case VISITOR:
            case FILE_FROM_VISITOR:
                return R.layout.item_message_sent;
            case INFO_OPERATOR_BUSY:
                return R.layout.item_op_busy_message;
            case INFO:
            case KEYBOARD:
            case KEYBOARD_RESPONSE:
            default:
                return R.layout.item_info_message;
        }
    }

    public boolean add(Message message) {
        return messageList.add(message);
    }

    public void add(int i, Message message) {
        messageList.add(i, message);
    }

    public boolean addAll(int i, Collection<? extends Message> collection) {
        return messageList.addAll(i, collection);
    }

    public Message set(int i, Message message) {
        return messageList.set(i, message);
    }

    public Message remove(int i) {
        return messageList.remove(i);
    }

    public void clear() {
        messageList.clear();
    }

    public int indexOf(Message message) {
        return messageList.indexOf(message);
    }

    public int lastIndexOf(Message message) {
        return messageList.lastIndexOf(message);
    }

    private void showMessage(String message, View view) {
        Toast.makeText(view.getContext().getApplicationContext(),
                message, Toast.LENGTH_SHORT).show();
    }

    class MessageHolder extends RecyclerView.ViewHolder {
        Message message;
        TextView messageText;
        TextView messageDate;
        TextView messageTime;
        ImageView messageTick;
        RelativeLayout quoteLayout;
        LinearLayout quote_body;
        TextView quoteSenderName;
        TextView quoteText;
        LinearLayout messageBody;

        MessageHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
            messageDate = itemView.findViewById(R.id.text_message_date);
            messageTime = itemView.findViewById(R.id.text_message_time);
            messageTick = itemView.findViewById(R.id.tick);
            quoteLayout = itemView.findViewById(R.id.quote_message);
            quote_body = itemView.findViewById(R.id.quote_body);
            quoteSenderName = itemView.findViewById(R.id.quote_sender_name);
            quoteText = itemView.findViewById(R.id.quote_text);
            messageBody = itemView.findViewById(R.id.message_body);
        }

        public void bind(final Message message, boolean showDate) {
            this.message = message;

            messageBody.setVisibility(View.GONE);
            if (messageText != null &&
                    message.getType() != FILE_FROM_OPERATOR &&
                    message.getType() != FILE_FROM_VISITOR) {
                messageBody.setVisibility(View.VISIBLE);
                messageText.setText(message.getText());
                messageText.setVisibility(View.VISIBLE);
            }
            if (messageDate != null) {
                if (showDate) {
                    messageDate
                            .setText(DateFormat.getDateFormat(webimChatFragment.getContext())
                                    .format(message.getTime()));
                    messageDate.setVisibility(View.VISIBLE);
                } else {
                    messageDate.setVisibility(View.GONE);
                }
            }
            if (messageTime != null) {
                messageTime
                        .setText(DateFormat.getTimeFormat(webimChatFragment.getContext())
                                .format(message.getTime()));
                messageText.setVisibility(View.VISIBLE);
            }

            if (messageTick != null) {
                messageTick.setImageResource(message.isReadByOperator()
                        ? R.drawable.ic_double_tick
                        : R.drawable.ic_tick);
                messageTick.setVisibility(message.getSendStatus() == SendStatus.SENT
                        ? View.VISIBLE
                        : View.GONE);
            }

            if (quoteLayout != null) {
                if (message.getQuote() != null) {
                    quoteLayout.setVisibility(View.VISIBLE);
                    quoteSenderName.setVisibility(View.VISIBLE);
                    quoteText.setVisibility(View.VISIBLE);
                    Resources resources = webimChatFragment.getResources();
                    Quote quote = message.getQuote();
                    String textQuoteSenderName = "";
                    String textQuote = "";
                    switch (quote.getState()) {
                        case PENDING:
                            textQuote = (message.getType() == OPERATOR)
                                    ? resources.getString(R.string.quote_is_pending)
                                    : quote.getMessageText();
                            textQuoteSenderName = (message.getType() == OPERATOR)
                                    ? ""
                                    : resources.getString(R.string.visitor_sender_name);
                            break;
                        case FILLED:
                            textQuote =
                                    (quote.getMessageType() == FILE_FROM_OPERATOR
                                            || quote.getMessageType() == FILE_FROM_VISITOR)
                                    ? quote.getMessageAttachment().getFileName()
                                    : quote.getMessageText();
                            textQuoteSenderName =
                                    (quote.getMessageType() == VISITOR
                                            || quote.getMessageType() == FILE_FROM_VISITOR)
                                    ? resources.getString(R.string.visitor_sender_name)
                                    : quote.getSenderName();
                            break;
                        case NOT_FOUND:
                            textQuote = resources.getString(R.string.quote_is_not_found);
                            quoteSenderName.setVisibility(View.GONE);
                            break;
                    }
                    quoteSenderName.setText(textQuoteSenderName);
                    quoteText.setText(textQuote);
                } else {
                    quoteLayout.setVisibility(View.GONE);
                    quoteSenderName.setVisibility(View.GONE);
                    quoteText.setVisibility(View.GONE);
                }
            }
        }
    }

    class FileMessageHolder extends MessageHolder {

        ImageView thumbView, quoteView, fileImage;
        ConstraintLayout layoutQuotedView, layoutFileImage;
        TextView senderNameForImage, fileName, fileSize, fileError;
        RelativeLayout layoutAttachedFile;
        ProgressBar progressFileUpload;

        FileMessageHolder(View itemView) {
            super(itemView);
            thumbView = itemView.findViewById(R.id.attached_image);
            quoteView = itemView.findViewById(R.id.quoted_image);
            layoutQuotedView = itemView.findViewById(R.id.const_quoted_image);
            senderNameForImage = itemView.findViewById(R.id.sender_name_for_image);
            layoutAttachedFile = itemView.findViewById(R.id.attached_file);
            fileImage = itemView.findViewById(R.id.file_image);
            layoutFileImage = itemView.findViewById(R.id.file_image_const);
            fileName = itemView.findViewById(R.id.file_name);
            fileSize = itemView.findViewById(R.id.file_size);
            fileError = itemView.findViewById(R.id.error_text);
            progressFileUpload = itemView.findViewById(R.id.progress_file_upload);
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            thumbView.setVisibility(View.GONE);
            layoutAttachedFile.setVisibility(View.GONE);
            layoutQuotedView.setVisibility(View.GONE);
            Attachment attachment = message.getAttachment();
            if (attachment == null) {
                if (thumbView != null && message.getType().equals(FILE_FROM_VISITOR)) {
                    thumbView.setVisibility(View.GONE);
                    messageBody.setVisibility(View.VISIBLE);
                    String textMessage =
                            webimChatFragment.getResources().getString(R.string.sending_file) +
                            message.getText();
                    messageText.setText(textMessage);
                    messageText.setVisibility(View.VISIBLE);
                }
            } else {
                messageBody.setVisibility(View.GONE);
                ImageInfo imageInfo = attachment.getFileInfo().getImageInfo();
                if (imageInfo == null || mustShowAttachmentView(imageInfo)) {
                    showAttachmentView(attachment);
                } else {
                    setViewSize(thumbView, getThumbSize(imageInfo));
                    addImageInView(attachment.getFileInfo(), thumbView, messageText);
                }
            }

            Message.Quote messageQuote = message.getQuote();
            if (messageQuote != null &&
                    messageQuote.getMessageAttachment() != null &&
                    messageQuote.getMessageAttachment().getImageInfo() != null) {
                FileInfo fileInfo = messageQuote.getMessageAttachment();
                addImageInView(fileInfo, quoteView, quoteText);
                layoutQuotedView.setVisibility(View.VISIBLE);
            }
        }

        private boolean mustShowAttachmentView(ImageInfo imageInfo) {
            int maxSide = Math.max(imageInfo.getWidth(), imageInfo.getHeight());
            int minSide = Math.min(imageInfo.getWidth(), imageInfo.getHeight());
            int imageSideRatio = maxSide / minSide;
            int MAX_ALLOWED_ASPECT_RATIO = 10;
            return imageSideRatio > MAX_ALLOWED_ASPECT_RATIO;
        }

        private void showAttachmentView(final Attachment attachment) {
            layoutAttachedFile.setVisibility(View.VISIBLE);
            fileName.setText(attachment.getFileInfo().getFileName());
            fileSize.setVisibility(View.GONE);

            switch (attachment.getState()) {
                case READY:
                    fileImage.setVisibility(View.VISIBLE);
                    if (attachment.getFileInfo().getImageInfo() != null) {
                        fileImage.setImageDrawable(webimChatFragment.getResources()
                                .getDrawable(R.drawable.ic_image_attachment));
                        layoutAttachedFile.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                openImageActivity(
                                        view,
                                        Uri.parse(attachment.getFileInfo().getUrl()));
                            }
                        });
                    } else {
                        fileImage.setImageDrawable(webimChatFragment.getResources()
                                .getDrawable(R.drawable.ic_download_file));
                        fileImage.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                downloadFile(attachment.getFileInfo());
                            }
                        });
                    }
                    progressFileUpload.setVisibility(View.GONE);
                    fileSize.setVisibility(View.VISIBLE);
                    String size = humanReadableByteCountBin(attachment.getFileInfo().getSize());
                    fileSize.setText(size);
                    fileError.setVisibility(View.GONE);
                    break;
                case UPLOAD:
                    if (progressFileUpload.getVisibility() == View.GONE) {
                        fileImage.setVisibility(View.INVISIBLE);
                        progressFileUpload.setVisibility(View.VISIBLE);
                    }
                    fileError.setVisibility(View.VISIBLE);
                    fileError.setText(webimChatFragment.getResources().getString(R.string.file_transfer_by_operator));
                    break;
                case ERROR:
                    fileImage.setImageDrawable(webimChatFragment.getResources()
                            .getDrawable(R.drawable.ic_error_download_file));
                    progressFileUpload.setVisibility(View.GONE);
                    fileError.setVisibility(View.VISIBLE);
                    fileError.setText(attachment.getErrorMessage());
                    fileImage.setVisibility(View.VISIBLE);
                    break;
            }
        }

        private void downloadFile(FileInfo file) {
            Context context = webimChatFragment.getContext();
            if (Build.VERSION.SDK_INT >= 23
                    && ActivityCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        webimChatFragment.getActivity(),
                        new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE },
                        1);
            } else {
                String fileName = file.getFileName();
                showMessage(context.getString(R.string.saving_file, fileName), itemView);

                DownloadManager manager =
                        (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager != null) {
                    DownloadManager.Request downloadRequest = new DownloadManager.Request(
                            Uri.parse(file.getUrl()));
                    downloadRequest.setTitle(fileName);
                    downloadRequest.allowScanningByMediaScanner();
                    downloadRequest.setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    downloadRequest.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            fileName);

                    manager.enqueue(downloadRequest);
                } else {
                    showMessage(context.getString(R.string.saving_failed), itemView);
                }
            }
        }

        private String humanReadableByteCountBin(long bytes) {
            long b = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
            return b < 1024L ? bytes + " B"
                    : b <= 0xfffccccccccccccL >> 40 ? String.format(Locale.getDefault(), "%.0f kB", bytes / 0x1p10)
                    : b <= 0xfffccccccccccccL >> 30 ? String.format(Locale.getDefault(), "%.0f MB", bytes / 0x1p20)
                    : b <= 0xfffccccccccccccL >> 20 ? String.format(Locale.getDefault(), "%.0f GB", bytes / 0x1p30)
                    : b <= 0xfffccccccccccccL >> 10 ? String.format(Locale.getDefault(), "%.0f TB", bytes / 0x1p40)
                    : b <= 0xfffccccccccccccL ? String.format(Locale.getDefault(), "%.0f PiB", (bytes >> 10) / 0x1p40)
                    : String.format(Locale.getDefault(), "%.0f EiB", (bytes >> 20) / 0x1p40);
        }

        private void addImageInView(FileInfo attachment,
                                    ImageView imageView,
                                    TextView textView) {
            String imageUrl = attachment.getImageInfo().getThumbUrl();
            final Uri fileUrl = Uri.parse(attachment.getUrl());
            Glide.with(webimChatFragment)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .into(imageView);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openImageActivity(view, fileUrl);
                }
            });
            textView.setText(
                    webimChatFragment.getResources().getString(R.string.reply_message_with_image));
            textView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.VISIBLE);
        }


        private Size getThumbSize(ImageInfo imageInfo) {
            Size size;
            int imageWidth = imageInfo.getWidth();
            int imageHeight = imageInfo.getHeight();
            boolean isPortraitOrientation = imageHeight > imageWidth;
            if (isPortraitOrientation) {
                ScaledSize scaledSize = scalingThumbSize(
                        imageWidth,
                        imageHeight,
                        isPortraitOrientation);
                size = new Size(scaledSize.getShotSide(), scaledSize.getLongSide());
            } else {
                ScaledSize scaledSize = scalingThumbSize(
                        imageHeight,
                        imageWidth,
                        isPortraitOrientation);
                size = new Size(scaledSize.getLongSide(), scaledSize.getShotSide());
            }
            return size;
        }

        private ScaledSize scalingThumbSize(double shotSide,
                                            double longSide,
                                            boolean portraitOrientationImage) {
            double maxRatioForView = portraitOrientationImage
                    ? 1
                    : 0.6;
            double minRatioForView = 0.17;
            int orientation = webimChatFragment.getActivity()
                    .getResources().getConfiguration().orientation;
            DisplayMetrics displayMetrics = new DisplayMetrics();
            webimChatFragment.getActivity().getWindowManager()
                    .getDefaultDisplay().getMetrics(displayMetrics);
            double shorterScreenSide = orientation == Configuration.ORIENTATION_PORTRAIT
                    ? displayMetrics.widthPixels
                    : displayMetrics.heightPixels;
            double maxSize = shorterScreenSide * maxRatioForView;
            double minSize = shorterScreenSide * minRatioForView;
            double newShotSide = maxSize / longSide * shotSide;
            if (newShotSide < minSize) {
                shotSide = minSize;
            } else {
                shotSide =  newShotSide > maxSize ? maxSize : newShotSide;
            }
            longSide = maxSize;
            if (portraitOrientationImage) {
                double maxRatioForPortraitView = 0.6;
                double maxWidth = shorterScreenSide * maxRatioForPortraitView;
                if (shotSide > maxWidth) {
                    longSide = maxWidth / shotSide * maxSize;
                    shotSide = maxWidth;
                }
            }
            return new ScaledSize((int) shotSide, (int) longSide);
        }

        private void setViewSize(ImageView view, Size size) {
            if (view.getParent() instanceof FrameLayout) {
                view.setLayoutParams(new FrameLayout.LayoutParams(size.getWidth(),
                        size.getHeight(), Gravity.END));
            }
            else {
                view.setLayoutParams(new LinearLayout.LayoutParams(size.getWidth(),
                        size.getHeight(), Gravity.END));
            }
        }

        private void openImageActivity(View view, Uri fileUrl) {
            Intent openImgIntent = new Intent(view.getContext(), ImageActivity.class);
            openImgIntent.setData(fileUrl);
            Activity activity = getRequiredActivity(view);
            if (activity != null) {
                activity.startActivity(openImgIntent);
                activity.overridePendingTransition(R.anim.pull_in_right, R.anim.push_out_left);
            }
        }

        private Activity getRequiredActivity(View view) {
            Context context = view.getContext();
            while (context instanceof ContextWrapper) {
                if (context instanceof Activity) {
                    return (Activity)context;
                }
                context = ((ContextWrapper)context).getBaseContext();
            }
            return null;
        }

        private class Size {
            private final int width;
            private final int height;

            private Size(int width, int height) {
                this.width = width;
                this.height = height;
            }

            int getWidth() {
                return width;
            }

            int getHeight() {
                return height;
            }
        }

        private class ScaledSize {
            private final int shotSide;
            private final int longSide;

            private ScaledSize(int shotSide, int longSide) {
                this.shotSide = shotSide;
                this.longSide = longSide;
            }

            int getShotSide() {
                return shotSide;
            }

            int getLongSide() {
                return longSide;
            }
        }
    }

    private class SentMessageHolder extends FileMessageHolder {
        ProgressBar sendingProgress;

        private float LEVEL_SHADING = 0.6f;

        SentMessageHolder(final View itemView) {
            super(itemView);

            sendingProgress = itemView.findViewById(R.id.sending_msg);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    final Dialog dialog = new Dialog(view.getContext());
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.dialog_message_menu);
                    if (dialog.getWindow() != null) {
                        WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                        layoutParams.dimAmount = LEVEL_SHADING;
                        dialog.getWindow().setAttributes(layoutParams);
                    }
                    RelativeLayout replyLayout = dialog.findViewById(R.id.relLayoutReply);
                    RelativeLayout copyLayout = dialog.findViewById(R.id.relLayoutCopy);
                    RelativeLayout editLayout = dialog.findViewById(R.id.relLayoutEdit);
                    RelativeLayout deleteLayout = dialog.findViewById(R.id.relLayoutDelete);

                    replyLayout.setVisibility(View.GONE);
                    copyLayout.setVisibility(View.GONE);
                    editLayout.setVisibility(View.GONE);
                    deleteLayout.setVisibility(View.GONE);

                    if (message.canBeReplied()) {
                        replyLayout.setVisibility(View.VISIBLE);
                        replyLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                webimChatFragment.onReplyMessageAction(message, getAdapterPosition());
                                dialog.dismiss();
                            }
                        });
                    }

                    if (message.getType() != FILE_FROM_VISITOR) {
                        copyLayout.setVisibility(View.VISIBLE);
                        copyLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ClipData clip =
                                        ClipData.newPlainText("message", message.getText());
                                ClipboardManager clipboard =
                                        (ClipboardManager) view.getContext()
                                                .getSystemService(Context.CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(clip);
                                    showMessage(view.getResources()
                                            .getString(R.string.copied_message), view);
                                } else {
                                    showMessage(view.getResources()
                                            .getString(R.string.copy_failed), view);
                                }
                                dialog.dismiss();
                            }
                        });
                        if (message.canBeEdited()) {
                            editLayout.setVisibility(View.VISIBLE);
                            editLayout.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    webimChatFragment.onEditAction(message);
                                    dialog.dismiss();
                                }
                            });
                        }
                    }

                    if (message.canBeEdited()) {
                        deleteLayout.setVisibility(View.VISIBLE);
                        deleteLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                webimChatFragment.onDeleteMessageAction(message);
                                dialog.dismiss();
                            }
                        });
                    }

                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            changeBackgroundDrawable(
                                    (message.getType() == FILE_FROM_VISITOR)
                                            ? layoutAttachedFile
                                            : messageBody,
                                    R.drawable.background_send_message,
                                    R.color.sendingMsgBubble);
                        }
                    });

                    if (replyLayout.getVisibility() == View.VISIBLE
                            || copyLayout.getVisibility() == View.VISIBLE
                            || deleteLayout.getVisibility() == View.VISIBLE) {
                        changeBackgroundDrawable(
                                (message.getType() == FILE_FROM_VISITOR)
                                        ? layoutAttachedFile
                                        : messageBody,
                                R.drawable.background_send_message,
                                R.color.sendingMsgSelect);
                        dialog.show();
                    }

                }
            });
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);

            boolean sending = message.getSendStatus() == SendStatus.SENDING;
            if (messageTime != null) {
                messageTime.setVisibility(sending ? View.GONE : View.VISIBLE);
            }
            if (sendingProgress != null) {
                sendingProgress.setVisibility(sending ? View.VISIBLE : View.GONE);
            }
        }
    }

    private class ReceivedMessageHolder extends FileMessageHolder {
        boolean showSenderInfo = true;
        TextView timeText, nameText, nameTextForImage, nameTextForFile;
        ImageView profileImage;

        private float LEVEL_SHADING = 0.6f;

        ReceivedMessageHolder(final View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.text_message_time);
            nameText = itemView.findViewById(R.id.sender_name);
            nameTextForImage = itemView.findViewById(R.id.sender_name_for_image);
            nameTextForFile = itemView.findViewById(R.id.sender_name_for_file);
            profileImage = itemView.findViewById(R.id.sender_photo);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    final Dialog dialog = new Dialog(view.getContext());
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    dialog.setContentView(R.layout.dialog_message_menu);
                    if (dialog.getWindow() != null) {
                        WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                        layoutParams.dimAmount = LEVEL_SHADING;
                        dialog.getWindow().setAttributes(layoutParams);
                    }
                    RelativeLayout replyLayout = dialog.findViewById(R.id.relLayoutReply);
                    RelativeLayout copyLayout = dialog.findViewById(R.id.relLayoutCopy);
                    RelativeLayout editLayout = dialog.findViewById(R.id.relLayoutEdit);
                    RelativeLayout deleteLayout = dialog.findViewById(R.id.relLayoutDelete);

                    replyLayout.setVisibility(View.GONE);
                    copyLayout.setVisibility(View.GONE);
                    editLayout.setVisibility(View.GONE);
                    deleteLayout.setVisibility(View.GONE);

                    if (message.canBeReplied()) {
                        replyLayout.setVisibility(View.VISIBLE);
                        replyLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                webimChatFragment.onReplyMessageAction(message, getAdapterPosition());
                                dialog.dismiss();
                            }
                        });
                    }
                    if (message.getType() != FILE_FROM_OPERATOR) {
                        copyLayout.setVisibility(View.VISIBLE);
                        copyLayout.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                ClipData clip =
                                        ClipData.newPlainText("message", message.getText());
                                ClipboardManager clipboard =
                                        (ClipboardManager) view.getContext()
                                                .getSystemService(Context.CLIPBOARD_SERVICE);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(clip);
                                    showMessage(view.getResources()
                                            .getString(R.string.copied_message), view);
                                } else {
                                    showMessage(view.getResources()
                                            .getString(R.string.copy_failed), view);
                                }
                                dialog.dismiss();
                            }
                        });
                    }

                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            changeBackgroundDrawable(
                                    (message.getType() == FILE_FROM_OPERATOR)
                                            ? layoutAttachedFile
                                            : messageBody,
                                    R.drawable.background_received_message,
                                    R.color.receivedMsgBubble);
                        }
                    });

                    if (replyLayout.getVisibility() == View.VISIBLE
                            || copyLayout.getVisibility() == View.VISIBLE) {
                        changeBackgroundDrawable(
                                (message.getType() == FILE_FROM_OPERATOR)
                                        ? layoutAttachedFile
                                        : messageBody,
                                R.drawable.background_received_message,
                                R.color.receivedMsgSelect);
                        dialog.show();
                    }
                }
            });
        }

        @Override
        public void bind(Message message, boolean showDate) {
            super.bind(message, showDate);
            timeText.setText(DateFormat.getTimeFormat(webimChatFragment.getContext())
                    .format(message.getTime()));
            if (showSenderInfo) {
                if (message.getType().equals(FILE_FROM_OPERATOR) && fileIsImage(message)) {
                    nameTextForImage.setVisibility(View.VISIBLE);
                    nameTextForImage.setText(message.getSenderName());
                } else {
                    nameTextForImage.setVisibility(View.GONE);
                    if (message.getType().equals(FILE_FROM_OPERATOR)) {
                        nameTextForFile.setVisibility(View.VISIBLE);
                        nameTextForFile.setText(message.getSenderName());
                    } else {
                        nameText.setVisibility(View.VISIBLE);
                        nameText.setText(message.getSenderName());
                    }
                }

                String avatarUrl = message.getSenderAvatarUrl();
                profileImage.setVisibility(View.VISIBLE);
                if (avatarUrl != null) {
                    if (!avatarUrl.equals(profileImage.getTag(R.id.avatarUrl))) {
                        Glide.with(webimChatFragment).load(avatarUrl).into(profileImage);
                        profileImage.setTag(R.id.avatarUrl, avatarUrl);
                    }
                } else {
                    profileImage.setImageDrawable(
                            webimChatFragment.getResources()
                                    .getDrawable(R.drawable.default_operator_avatar));
                    profileImage.setVisibility(View.VISIBLE);
                }
            } else {
                nameText.setVisibility(View.GONE);
                nameTextForImage.setVisibility(View.GONE);
                profileImage.setVisibility(View.GONE);
                showSenderInfo = true;
            }
        }
    }

    private boolean fileIsImage(Message message) {
        return message.getAttachment() != null
                && message.getAttachment().getFileInfo().getImageInfo() != null;
    }

    private void changeBackgroundDrawable(View layout, int drawableResource, int colorForSelect) {
        Drawable unwrappedDrawable =
                AppCompatResources.getDrawable(layout.getContext(), drawableResource);
        if (unwrappedDrawable != null) {
            DrawableCompat.setTint(
                    DrawableCompat.wrap(unwrappedDrawable),
                    layout.getResources().getColor(colorForSelect));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            layout.setBackground(layout.getResources().getDrawable(drawableResource));
        } else {
            layout.setBackgroundDrawable(layout.getResources().getDrawable(drawableResource));
        }
    }
}