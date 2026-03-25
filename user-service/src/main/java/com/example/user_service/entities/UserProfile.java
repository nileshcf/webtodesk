package com.example.user_service.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@Column
	private String name;

	@Column
	private Long phoneNumber;

	@Column
	private String avatarUrl;

	@OneToOne
	@JoinColumn(name = "user_id", unique = true)
	private User user;
}