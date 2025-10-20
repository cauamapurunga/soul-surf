package com.soulsurf.backend.services;

import com.soulsurf.backend.dto.NotificationDTO;
import com.soulsurf.backend.dto.UserDTO;
import com.soulsurf.backend.entities.Comment;
import com.soulsurf.backend.entities.Notification;
import com.soulsurf.backend.entities.Post;
import com.soulsurf.backend.entities.User;
import com.soulsurf.backend.repository.CommentRepository;
import com.soulsurf.backend.repository.NotificationRepository;
import com.soulsurf.backend.repository.PostRepository;
import com.soulsurf.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                              PostRepository postRepository, CommentRepository commentRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public void createMentionNotification(String senderUsername, String recipientUsername, Long postId, Long commentId) {
        User sender = userRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new RuntimeException("Usuário remetente não encontrado"));

        User recipient = userRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Usuário mencionado não encontrado"));

        // Não criar notificação se o usuário está se mencionando
        if (sender.getId().equals(recipient.getId())) {
            return;
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post não encontrado"));

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comentário não encontrado"));

        Notification notification = new Notification();
        notification.setSender(sender);
        notification.setRecipient(recipient);
        notification.setType("MENTION");
        notification.setPost(post);
        notification.setComment(comment);

        notificationRepository.save(notification);
    }

    @Transactional
    public List<NotificationDTO> getUserNotifications(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public int getUnreadCount(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        return notificationRepository.countByRecipientAndReadFalse(user);
    }

    @Transactional
    public void markAsRead(Long notificationId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notificação não encontrada"));

        // Verifica se a notificação pertence ao usuário atual
        if (!notification.getRecipient().getId().equals(user.getId())) {
            throw new RuntimeException("Esta notificação não pertence ao usuário atual");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private NotificationDTO convertToDTO(Notification notification) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(notification.getId());

        // Converter sender para UserDTO
        UserDTO senderDTO = new UserDTO();
        senderDTO.setId(notification.getSender().getId());
        senderDTO.setUsername(notification.getSender().getUsername());
        senderDTO.setEmail(notification.getSender().getEmail());
        senderDTO.setFotoPerfil(notification.getSender().getFotoPerfil());
        dto.setSender(senderDTO);

        dto.setType(notification.getType());

        if (notification.getPost() != null) {
            dto.setPostId(notification.getPost().getId());
        }

        if (notification.getComment() != null) {
            dto.setCommentId(notification.getComment().getId());
        }

        dto.setRead(notification.isRead());
        dto.setCreatedAt(notification.getCreatedAt());

        // Criar uma mensagem amigável para a notificação
        String message = "";
        if ("MENTION".equals(notification.getType())) {
            message = notification.getSender().getUsername() + " mencionou você em um comentário";
        }
        dto.setMessage(message);

        return dto;
    }
}
