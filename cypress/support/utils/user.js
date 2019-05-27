export class User {
  constructor(lastName) {
    cy.log(`Create user with lastName = ${lastName}`);
    this.lastName = lastName;
    return this; 
  }

  setSystemUser(isSystem) {
    cy.log(`User - set system user = ${isSystem}`);
    this.isSystem = isSystem;
    return this;
  }

  setFirstName(name) {
    cy.log(`User - set first name = ${name}`);
    this.firstName = name;
    return this;
  }

  setLastName(name) {
    cy.log(`User - set last name = ${name}`);
    this.lastName = name;
    return this;
  }

  setEmail(email) {
    cy.log(`User - set email = ${email}`);
    this.email = email;
    return this;
  }

  setPassword(password) {
    cy.log(`User - set password = ${password}`);
    this.password = password;
    return this;
  }

  apply() {
    cy.log(`User - apply - START (name=${this.firstName})`);
    applyUser(this);
    cy.log(`User - apply - END (name=${this.firstName})`);
    return this;
  }
}

function applyUser(user) {
  describe(`Create new user ${user.name}`, function() {
    it('Set names', function() {
      cy.visitWindow('108', 'NEW');

      cy.writeIntoStringField('Firstname', user.firstName);
      cy.writeIntoStringField('Lasttname', user.lastName);
      cy.writeIntoStringField('Email', user.email);
    });
  });
}
