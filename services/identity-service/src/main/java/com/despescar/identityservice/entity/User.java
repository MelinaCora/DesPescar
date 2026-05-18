package com.despescar.identityservice.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name="users")


public class User {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
	
	@Column (nullable=false)
	private String firstName;
	
	@Column (nullable = false)
	private String lastName;
	
	@Column (nullable = false, unique =true)
	private String email;
	
	@Column (nullable = false)
	private String password;
	
	@Column (nullable = false)
	private LocalDate registrationDate = LocalDate.now();
	
	@Column (nullable = false)
	private Boolean isActive = true;
	
	@ManyToOne
	@JoinColumn(name = "role_id")
	private Role role;

}
