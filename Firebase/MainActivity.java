package com.example.user.firebasechat;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.text.format.DateFormat;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.user.firebasechat.entity.ChatMessage;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseListAdapter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class MainActivity extends AppCompatActivity {

    private static final int SIGN_IN_REQUEST_CODE = 0xC8;
    private static final int PICKFILE_RESULT_CODE = 1;
    private static final int EDIT_MESSAGE_CODE = 0x11;

    private ImageButton sendBtn;
    private EditText messageText;
    private ListView messagesListView;

    private FirebaseListAdapter<ChatMessage> adapter;

    private Uri fileUri = Uri.EMPTY;
    private FirebaseStorage storage;
    private StorageReference storageRef;

    ActionMode actionMode;
    int selectedPosition = 0;

    SearchView searchView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        messageText = (EditText)findViewById(R.id.messageText);
        messagesListView = (ListView)findViewById(R.id.messagesView);

        sendBtn = (ImageButton)findViewById(R.id.sendButton);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Read the input field and push a new instance
                // of ChatMessage to the Firebase database
                FirebaseDatabase.getInstance()
                        .getReference()
                        .push()
                        .setValue(new ChatMessage(messageText.getText().toString(),
                                FirebaseAuth.getInstance()
                                        .getCurrentUser()
                                        .getDisplayName())
                        );

                // Clear the input
                messageText.setText("");
            }
        });

        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            // Start sign in/sign up activity
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .build(),
                    SIGN_IN_REQUEST_CODE
            );
        } else {
            // User is already signed in. Therefore, display
            // a welcome Toast
            Toast.makeText(this,
                    "Welcome " + FirebaseAuth.getInstance()
                            .getCurrentUser()
                            .getDisplayName(),
                    Toast.LENGTH_LONG)
                    .show();

            // Load chat room contents
            displayChatMessages();
        }

        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();

        messagesListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        messagesListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                selectedPosition = position;
                if (actionMode == null)
                    actionMode = startActionMode(callback);
                else
                    actionMode.finish();

                return true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == SIGN_IN_REQUEST_CODE) {
            if(resultCode == RESULT_OK) {
                Toast.makeText(this,
                        "Successfully signed in. Welcome!",
                        Toast.LENGTH_LONG)
                        .show();
                displayChatMessages();
            } else {
                Toast.makeText(this,
                        "We couldn't sign you in. Please try again later.",
                        Toast.LENGTH_LONG)
                        .show();

                // Close the app
                finish();
            }
        }else if(requestCode == PICKFILE_RESULT_CODE){
            if(resultCode == RESULT_OK){
                fileUri = data.getData();
            }else{
                fileUri = Uri.EMPTY;
            }

            if(!fileUri.equals(Uri.EMPTY)){
                Uri file = fileUri;
                StorageReference riversRef = storageRef.child("files/"+file.getLastPathSegment());
                UploadTask uploadTask = riversRef.putFile(file);
                uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Toast.makeText(MainActivity.this, "Upload error", Toast.LENGTH_SHORT).show();
                    }
                }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        FirebaseDatabase.getInstance()
                                .getReference()
                                .push()
                                .setValue(new ChatMessage(taskSnapshot.getDownloadUrl().toString(),
                                        FirebaseAuth.getInstance()
                                                .getCurrentUser()
                                                .getDisplayName())
                                );
                    }
                });
            }
        }
        else if(requestCode == EDIT_MESSAGE_CODE){
            if(resultCode == RESULT_OK) {

                if(data == null){return;}

                String message = data.getStringExtra("MESSAGE");
                adapter.getRef(selectedPosition)
                        .child("messageText")
                        .setValue(message);
                adapter.notifyDataSetChanged();
            }
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.searchView);
        searchView = (SearchView)searchItem.getActionView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.menu_sign_out) {
            AuthUI.getInstance().signOut(this)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            Toast.makeText(MainActivity.this,
                                    "You have been signed out.",
                                    Toast.LENGTH_LONG)
                                    .show();

                            // Close activity
                            finish();
                        }
                    });
        }else if(item.getItemId() == R.id.menu_upload_file){
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "Select file"), PICKFILE_RESULT_CODE);
        }
        return true;
    }

    private void displayChatMessages() {
        adapter = new FirebaseListAdapter<ChatMessage>(this, ChatMessage.class,
                R.layout.messages_item, FirebaseDatabase.getInstance().getReference()) {
            @Override
            protected void populateView(View v, ChatMessage model, int position) {
                // Get references to the views of message.xml
                TextView messageText = (TextView)v.findViewById(R.id.message_text);
                TextView messageUser = (TextView)v.findViewById(R.id.message_user);
                TextView messageTime = (TextView)v.findViewById(R.id.message_time);

                // Set their text
                messageText.setText(model.getMessageText());
                messageUser.setText(model.getMessageUser());

                // Format the date before showing it
                messageTime.setText(DateFormat.format("dd-MM-yyyy (HH:mm:ss)",
                        model.getMessageTime()));

            }


        };

        messagesListView.setAdapter(adapter);
    }

    private ActionMode.Callback callback = new ActionMode.Callback() {

        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.main_menu, menu);
            menu.getItem(0).setVisible(false);
            menu.getItem(1).setVisible(false);
            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() == R.id.menu_delete){
                if(adapter.getItem(selectedPosition).getMessageUser()
                        .equals(FirebaseAuth.getInstance().getCurrentUser().getDisplayName())) {
                    adapter.getRef(selectedPosition).removeValue();
                    adapter.notifyDataSetChanged();
                }else{
                    Toast.makeText(MainActivity.this, "You can not delete someone else's message", Toast.LENGTH_SHORT).show();
                }
            }
            if(item.getItemId() == R.id.editMenu){
                if(adapter.getItem(selectedPosition).getMessageUser()
                        .equals(FirebaseAuth.getInstance().getCurrentUser().getDisplayName())) {
                    Intent intent = new Intent(MainActivity.this, EditActivity.class);
                    intent.putExtra("MESSAGE", adapter.getItem(selectedPosition).getMessageText());
                    startActivityForResult(intent, EDIT_MESSAGE_CODE);
                }else{
                    Toast.makeText(MainActivity.this, "You can not edit someone else's message", Toast.LENGTH_SHORT).show();
                }
            }
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
        }

    };

}
